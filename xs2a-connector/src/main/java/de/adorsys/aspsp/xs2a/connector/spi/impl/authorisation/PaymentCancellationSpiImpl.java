/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiCommonPaymentTOMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PaymentCancellationSpiImpl extends AbstractAuthorisationSpi<SpiPayment, SCAPaymentResponseTO> implements PaymentCancellationSpi {
    private static final String ATTEMPT_FAILURE = "SCA_VALIDATION_ATTEMPT_FAILED";
    private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationSpiImpl.class);

    private final PaymentRestClient paymentRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final RedirectScaRestClient redirectScaRestClient;
    private final ScaResponseMapper scaResponseMapper;

    public PaymentCancellationSpiImpl(PaymentRestClient ledgersRestClient,
                                      ScaMethodConverter scaMethodConverter,
                                      AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                      GeneralAuthorisationService authorisationService,
                                      FeignExceptionReader feignExceptionReader,
                                      RedirectScaRestClient redirectScaRestClient,
                                      ScaResponseMapper scaResponseMapper,
                                      KeycloakTokenService keycloakTokenService,
                                      GeneralPaymentService paymentService,
                                      LedgersSpiCommonPaymentTOMapper ledgersSpiCommonPaymentTOMapper) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader, keycloakTokenService, redirectScaRestClient, paymentService, ledgersSpiCommonPaymentTOMapper);
        this.paymentRestClient = ledgersRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
        this.redirectScaRestClient = redirectScaRestClient;
        this.scaResponseMapper = scaResponseMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiContextData contextData,
                                                                                            @NotNull SpiPayment payment,
                                                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SpiPaymentCancellationResponse response = new SpiPaymentCancellationResponse();
        boolean cancellationMandated = payment.getPaymentStatus() != TransactionStatus.RCVD;
        response.setCancellationAuthorisationMandated(cancellationMandated);
        response.setTransactionStatus(payment.getPaymentStatus());
        return SpiResponse.<SpiPaymentCancellationResponse>builder()
                       .payload(response).build();
    }

    /**
     * Makes no sense.
     */
    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiContextData contextData,
                                                                                  @NotNull SpiPayment payment,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (payment.getPaymentStatus() == TransactionStatus.RCVD) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .payload(SpiResponse.voidResponse())
                           .build();
        }

        SCAPaymentResponseTO sca = getScaObjectResponse(aspspConsentDataProvider, true);
        if (sca.getScaStatus() == ScaStatusTO.EXEMPTED) {
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
            try {
                paymentRestClient.initiatePmtCancellation(payment.getPaymentId());
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .payload(SpiResponse.voidResponse())
                               .build();
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                logger.error("Cancel payment without SCA failed: payment ID: {}, devMessage: {}", payment.getPaymentId(), devMessage);
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR_CANCELLATION, devMessage))
                               .build();
            }
        }
        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .error(new TppMessage(MessageErrorCode.CANCELLATION_INVALID))
                       .build();
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndCancelPaymentWithResponse(@NotNull SpiContextData contextData,
                                                                                                                @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                                                @NotNull SpiPayment payment,
                                                                                                                @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<GlobalScaResponseTO> authorizeCancelPaymentResponse = redirectScaRestClient.validateScaCode(sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());

            if (authorizeCancelPaymentResponse.getStatusCode() == HttpStatus.OK) {
                String authCancellationBearerToken = authorizeCancelPaymentResponse.getBody().getBearerToken().getAccess_token();
                authRequestInterceptor.setAccessToken(authCancellationBearerToken);

                paymentRestClient.executeCancelPayment(sca.getPaymentId());
                SCAPaymentResponseTO paymentResponseTO = scaResponseMapper.mapToScaPaymentResponse(authorizeCancelPaymentResponse.getBody());

                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(paymentResponseTO));
                authRequestInterceptor.setAccessToken(authCancellationBearerToken);

                return SpiResponse.<SpiPaymentExecutionResponse>builder()
                               .payload(new SpiPaymentExecutionResponse(SpiAuthorisationStatus.SUCCESS))
                               .build();
            }

            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.UNAUTHORIZED_CANCELLATION))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Verify SCA authorisation and cancel payment failed: payment ID: {}, devMessage: {}", payment.getPaymentId(), devMessage);

            String errorCode = feignExceptionReader.getErrorCode(feignException);
            if (errorCode.equals(ATTEMPT_FAILURE)) {
                return SpiResponse.<SpiPaymentExecutionResponse>builder()
                               .payload(new SpiPaymentExecutionResponse(SpiAuthorisationStatus.ATTEMPT_FAILURE))
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                               .build();
            }
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID))
                           .build();
        }
    }

    @Override
    protected ResponseEntity<SCAPaymentResponseTO> getSelectMethodResponse(@NotNull String authenticationMethodId, SCAPaymentResponseTO sca) {

        ResponseEntity<GlobalScaResponseTO> scaResponse = redirectScaRestClient.selectMethod(sca.getAuthorisationId(), authenticationMethodId);

        return scaResponse.getStatusCode() == HttpStatus.OK
                       ? ResponseEntity.ok(scaResponseMapper.mapToScaPaymentResponse(scaResponse.getBody()))
                       : ResponseEntity.badRequest().build();
    }

    @Override
    protected SCAPaymentResponseTO getScaObjectResponse(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        return consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class, checkCredentials);
    }

    @Override
    protected String getBusinessObjectId(SpiPayment businessObject) {
        return businessObject.getPaymentId();
    }

    @Override
    protected OpTypeTO getOpType() {
        return OpTypeTO.CANCEL_PAYMENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiPayment businessObject) {
        logger.error("Authorising payment cancellation failed, payment ID: {}", businessObject.getPaymentId());
        return new TppMessage(MessageErrorCode.PAYMENT_FAILED);
    }

    @Override
    protected SpiResponse<SpiPsuAuthorisationResponse> onSuccessfulAuthorisation(SpiPayment businessObject,
                                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                                 SpiResponse<SpiPsuAuthorisationResponse> authorisePsu,
                                                                                 SCAPaymentResponseTO scaBusinessObjectResponse) {

        ResponseEntity<SCAPaymentResponseTO> cancellationResponse = paymentRestClient.executeCancelPayment(businessObject.getPaymentId());

        boolean success = Optional.ofNullable(cancellationResponse.getBody())
                                  .map(cr -> cr.getScaStatus() != ScaStatusTO.FAILED)
                                  .orElse(false);

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(cancellationResponse.getBody()));

        return success ? SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                 .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                 .build()
                       : SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                 .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.FAILURE))
                                 .build();
    }

    @Override
    protected SCAResponseTO initiateBusinessObject(SpiPayment businessObject, byte[] aspspConsentData) {
        return null;
    }

//    @Override
//    protected SCAPaymentResponseTO mapToScaResponse(SpiPayment businessObject, byte[] aspspConsentData, SCAPaymentResponseTO originalResponse) throws IOException {
//        String paymentTypeString = Optional.ofNullable(businessObject.getPaymentType()).orElseThrow(() -> new IOException("Missing payment type")).name();
//        SCALoginResponseTO scaResponseTO = consentDataService.response(aspspConsentData, SCALoginResponseTO.class);
//        SCAPaymentResponseTO paymentResponse = scaLoginMapper.toPaymentResponse(scaResponseTO);
//        paymentResponse.setObjectType(SCAPaymentResponseTO.class.getSimpleName());
//        paymentResponse.setPaymentId(businessObject.getPaymentId());
//        paymentResponse.setPaymentType(PaymentTypeTO.valueOf(paymentTypeString));
//        paymentResponse.setPaymentProduct(businessObject.getPaymentProduct());
//        paymentResponse.setMultilevelScaRequired(originalResponse.isMultilevelScaRequired());
//        return paymentResponse;
//    }

    @Override
    protected boolean validateStatuses(SpiPayment businessObject, SCAPaymentResponseTO sca) {
        return businessObject.getPaymentStatus() == TransactionStatus.RCVD ||
                       sca.getScaStatus() == ScaStatusTO.EXEMPTED;
    }

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiPayment businessObject, SCAPaymentResponseTO scaPaymentResponseTO) {
        return true;
    }

    @Override
    protected Optional<List<ScaUserDataTO>> getScaMethods(SCAPaymentResponseTO sca) {
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        ResponseEntity<GlobalScaResponseTO> cancelScaResponse = redirectScaRestClient.getSCA(sca.getAuthorisationId());

        return Optional.ofNullable(
                scaResponseMapper.mapToScaPaymentResponse(cancelScaResponse.getBody()).getScaMethods()
        );
    }

    @Override
    public @NotNull SpiResponse<Boolean> requestTrustedBeneficiaryFlag(@NotNull SpiContextData spiContextData, @NotNull SpiPayment payment, @NotNull String authorisationId, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        // TODO replace with real response from ledgers https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1263
        logger.info("Retrieving mock trusted beneficiaries flag for payment cancellation: {}", payment);
        return SpiResponse.<Boolean>builder()
                       .payload(true)
                       .build();
    }
}
