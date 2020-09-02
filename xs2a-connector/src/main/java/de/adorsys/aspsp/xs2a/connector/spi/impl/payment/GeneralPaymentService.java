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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.*;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.ledgers.util.Ids;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.*;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@PropertySource("classpath:mock-data.properties")
public class GeneralPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(GeneralPaymentService.class);
    private static final String XML_MEDIA_TYPE = "application/xml";
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI for this payment";
    private static final String ATTEMPT_FAILURE = "SCA_VALIDATION_ATTEMPT_FAILED";

    private final PaymentRestClient paymentRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final String transactionStatusXmlBody;
    private final MultilevelScaService multilevelScaService;
    private final UserMgmtRestClient userMgmtRestClient;
    private final RedirectScaRestClient redirectScaRestClient;
    private final ScaResponseMapper scaResponseMapper;

    public GeneralPaymentService(PaymentRestClient ledgersRestClient,
                                 AuthRequestInterceptor authRequestInterceptor,
                                 AspspConsentDataService consentDataService,
                                 FeignExceptionReader feignExceptionReader,
                                 @Value("${test-transaction-status-xml-body}") String transactionStatusXmlBody,
                                 MultilevelScaService multilevelScaService,
                                 UserMgmtRestClient userMgmtRestClient,
                                 RedirectScaRestClient redirectScaRestClient,
                                 ScaResponseMapper scaResponseMapper) {
        this.paymentRestClient = ledgersRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
        this.transactionStatusXmlBody = transactionStatusXmlBody;
        this.multilevelScaService = multilevelScaService;
        this.userMgmtRestClient = userMgmtRestClient;
        this.redirectScaRestClient = redirectScaRestClient;
        this.scaResponseMapper = scaResponseMapper;
    }

    /**
     * Instantiating the very first response object.
     *
     * @param paymentType              the payment type
     * @param payment                  the payment object
     * @param aspspConsentDataProvider the credential data container access
     * @param responsePayload          the instantiated payload object
     */
    public <T extends SpiPaymentInitiationResponse> SpiResponse<T> firstCallInstantiatingPayment(
            @NotNull PaymentTypeTO paymentType, @NotNull SpiPayment payment,
            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, T responsePayload,
            @NotNull SpiPsuData spiPsuData, Set<SpiAccountReference> spiAccountReferences
    ) {
        String paymentId = StringUtils.isNotBlank(payment.getPaymentId())
                                   ? payment.getPaymentId()
                                   : Ids.id();
        GlobalScaResponseTO response = new GlobalScaResponseTO();
        response.setOperationObjectId(paymentId);
        response.setOpType(OpTypeTO.PAYMENT);
        responsePayload.setPaymentId(paymentId);
        responsePayload.setTransactionStatus(TransactionStatus.RCVD);

        boolean isMultilevelScaRequired;

        try {
            isMultilevelScaRequired = multilevelScaService.isMultilevelScaRequired(spiPsuData, spiAccountReferences);
        } catch (FeignException e) {
            logger.error("Error during REST call for payment initiation to ledgers for account multilevel checking, PSU ID: {}", spiPsuData.getPsuId());
            return SpiResponse.<T>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT))
                           .build();
        }

        response.setMultilevelScaRequired(isMultilevelScaRequired);
        responsePayload.setMultilevelScaRequired(isMultilevelScaRequired);

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response, false));

        return SpiResponse.<T>builder()
                       .payload(responsePayload)
                       .build();
    }

    public SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull PaymentTypeTO paymentType,
                                                                         @NotNull String acceptMediaType,
                                                                         @NotNull String paymentId,
                                                                         @NotNull TransactionStatus spiTransactionStatus,
                                                                         @NotNull byte[] aspspConsentData) {
        if (acceptMediaType.equals(XML_MEDIA_TYPE)) {
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .payload(new SpiGetPaymentStatusResponse(spiTransactionStatus, null, SpiGetPaymentStatusResponse.RESPONSE_TYPE_XML, transactionStatusXmlBody.getBytes(), PSU_MESSAGE))
                           .build();
        }

        if (!TransactionStatus.ACSP.equals(spiTransactionStatus)) {
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .payload(new SpiGetPaymentStatusResponse(spiTransactionStatus, null, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null, PSU_MESSAGE))
                           .build();
        }
        try {
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentData);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment status by ID with type: {} and ID: {}", paymentType, paymentId);
            TransactionStatusTO response = paymentRestClient.getPaymentStatusById(sca.getOperationObjectId()).getBody();
            TransactionStatus status = Optional.ofNullable(response)
                                               .map(r -> TransactionStatus.valueOf(r.name()))
                                               .orElseThrow(() -> FeignException.errorStatus("Request failed, response was 200, but body was empty!",
                                                                                             Response.builder().status(HttpStatus.BAD_REQUEST.value()).build()));
            logger.info("Transaction status: {}", status);
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .payload(new SpiGetPaymentStatusResponse(status, null, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null, PSU_MESSAGE))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Get payment status by ID failed: payment ID: {}, devMessage: {}", paymentId, devMessage);
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR, devMessage))
                           .build();

        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public SpiResponse<SpiPaymentResponse> verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(@NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<GlobalScaResponseTO> paymentAuthorisationValidationResponse = redirectScaRestClient.validateScaCode(sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());

            if (paymentAuthorisationValidationResponse.getStatusCode() == HttpStatus.OK) {
                GlobalScaResponseTO paymentAuthorisationValidationResponseBody = paymentAuthorisationValidationResponse.getBody();
                String authorisationBearerToken = paymentAuthorisationValidationResponseBody.getBearerToken().getAccess_token();

                authRequestInterceptor.setAccessToken(authorisationBearerToken);

                paymentRestClient.executePayment(sca.getOperationObjectId());

                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(paymentAuthorisationValidationResponseBody));

                String scaStatus = Optional.ofNullable(paymentAuthorisationValidationResponseBody)
                                           .map(GlobalScaResponseTO::getScaStatus)
                                           .map(ScaStatusTO::name)
                                           .orElse(null);

                logger.info("SCA status is: {}", scaStatus);

                authRequestInterceptor.setAccessToken(authorisationBearerToken);

                ResponseEntity<TransactionStatusTO> paymentStatusResponse = paymentRestClient.getPaymentStatusById(sca.getOperationObjectId());

                return SpiResponse.<SpiPaymentResponse>builder()
                               .payload(spiPaymentExecutionResponse(paymentStatusResponse.getBody()))
                               .build();
            }

            return SpiResponse.<SpiPaymentResponse>builder()
                           .payload(new SpiPaymentResponse(SpiAuthorisationStatus.FAILURE))
                           .build();

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.info("Verify SCA authorisation and execute payment failed: payment ID: {}, devMessage: {}", spiScaConfirmation.getPaymentId(), devMessage);

            String errorCode = feignExceptionReader.getErrorCode(feignException);
            if (errorCode.equals(ATTEMPT_FAILURE)) {
                return SpiResponse.<SpiPaymentResponse>builder()
                               .payload(new SpiPaymentResponse(SpiAuthorisationStatus.ATTEMPT_FAILURE))
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                               .build();
            }

            return SpiResponse.<SpiPaymentResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } catch (Exception exception) {
            return SpiResponse.<SpiPaymentResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR_PAYMENT_NOT_EXECUTED))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public SpiResponse<SpiPaymentConfirmationCodeValidationResponse> checkConfirmationCode(@NotNull SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest,
                                                                                           @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {

        try {
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<AuthConfirmationTO> authConfirmationTOResponse =
                    userMgmtRestClient.verifyAuthConfirmationCode(spiCheckConfirmationCodeRequest.getAuthorisationId(), spiCheckConfirmationCodeRequest.getConfirmationCode());

            AuthConfirmationTO authConfirmationTO = authConfirmationTOResponse.getBody();

            if (authConfirmationTO == null || !authConfirmationTO.isSuccess()) {
                // No response in payload from ASPSP or confirmation code verification failed at ASPSP side.
                return buildFailedConfirmationCodeResponse();
            }

            if (authConfirmationTO.isPartiallyAuthorised()) {
                // This authorisation is finished, but others are left.
                return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, TransactionStatus.PATC);
            }

            Optional<TransactionStatus> xs2aTransactionStatus = Optional.ofNullable(authConfirmationTO.getTransactionStatus())
                                                                        .map(TransactionStatusTO::getName)
                                                                        .map(TransactionStatus::getByValue);
            return xs2aTransactionStatus
                           .map(transactionStatus -> getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, transactionStatus))
                           .orElse(buildFailedConfirmationCodeResponse());
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            return SpiResponse.<SpiPaymentConfirmationCodeValidationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            // First check if there is any payment response ongoing.
            GlobalScaResponseTO response = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(response.getBearerToken().getAccess_token());

            ScaStatusTO scaStatus = response.getScaStatus();
            String scaStatusName = scaStatus.name();

            logger.info("Getting payment transaction status by payment id {}", response.getOperationObjectId());
            TransactionStatusTO transactionStatusTO = paymentRestClient.getPaymentStatusById(response.getOperationObjectId()).getBody();

            if (ScaStatusTO.EXEMPTED.equals(scaStatus) || ScaStatusTO.FINALISED.equals(scaStatus)) {
                // Success

                logger.info("SCA status is: {}", scaStatusName);
                logger.info("Payment scheduled for execution. Transaction status is: {}. Also see SCA status", transactionStatusTO);

                return SpiResponse.<SpiPaymentExecutionResponse>builder()
                               .payload(spiPaymentExecutionResponse(transactionStatusTO))
                               .build();
            }

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR_PAYMENT_NOT_EXECUTED, transactionStatusTO, scaStatusName))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Execute payment without SCA failed: devMessage {}", devMessage);
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR, devMessage))
                           .build();
        }
    }

    public <P extends SpiPayment> SpiResponse<P> getPaymentById(P payment, SpiAspspConsentDataProvider aspspConsentDataProvider, Function<PaymentTO, P> mapperToSpiPayment) {

        Function<P, SpiResponse<P>> buildSuccessResponse = p -> SpiResponse.<P>builder().payload(p).build();

        if (!TransactionStatus.ACSP.equals(payment.getPaymentStatus())) {
            return buildSuccessResponse.apply(payment);
        }

        Supplier<SpiResponse<P>> buildFailedResponse = () -> SpiResponse.<P>builder().error(new TppMessage(MessageErrorCode.PAYMENT_FAILED_INCORRECT_ID)).build();

        return getPaymentFromLedgers(payment, aspspConsentDataProvider.loadAspspConsentData())
                       .map(mapperToSpiPayment)
                       .map(buildSuccessResponse)
                       .orElseGet(buildFailedResponse);
    }

    public <P> GlobalScaResponseTO initiatePaymentInLedgers(P payment, PaymentTypeTO paymentTypeTO, PaymentTO request) {
        try {
            SCAPaymentResponseTO initiationResponse = paymentRestClient.initiatePayment(paymentTypeTO, request).getBody();
            logger.debug("{} payment body: {}", paymentTypeTO, payment);
            return scaResponseMapper.toGlobalScaResponse(initiationResponse);
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public GlobalScaResponseTO initiatePaymentCancellationInLedgers(String paymentId) {
        try {
            SCAPaymentResponseTO cancellationResponse = paymentRestClient.initiatePmtCancellation(paymentId).getBody();
            logger.debug("Payment cancellation, ID: {}", paymentId);
            return scaResponseMapper.toGlobalScaResponse(cancellationResponse);
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private SpiResponse<SpiPaymentConfirmationCodeValidationResponse> buildFailedConfirmationCodeResponse() {
        return getConfirmationCodeResponseForXs2a(ScaStatus.FAILED, TransactionStatus.RJCT);
    }

    private SpiResponse<SpiPaymentConfirmationCodeValidationResponse> getConfirmationCodeResponseForXs2a(ScaStatus scaStatus, TransactionStatus transactionStatus) {
        SpiPaymentConfirmationCodeValidationResponse response = new SpiPaymentConfirmationCodeValidationResponse(scaStatus, transactionStatus);

        return SpiResponse.<SpiPaymentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
    }

    private Optional<PaymentTO> getPaymentFromLedgers(SpiPayment payment, byte[] aspspConsentData) {
        try {
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentData);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment by ID with type: {} and ID: {}", payment.getPaymentType(), payment.getPaymentId());
            logger.debug("Payment body: {}", payment);
            return Optional.ofNullable(paymentRestClient.getPaymentById(sca.getOperationObjectId()).getBody());
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Get payment by ID failed: payment ID: {}, devMessage: {}", payment.getPaymentId(), devMessage);
            return Optional.empty();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private SpiPaymentExecutionResponse spiPaymentExecutionResponse(TransactionStatusTO transactionStatus) {
        return new SpiPaymentExecutionResponse(TransactionStatus.valueOf(transactionStatus.name()));
    }
}
