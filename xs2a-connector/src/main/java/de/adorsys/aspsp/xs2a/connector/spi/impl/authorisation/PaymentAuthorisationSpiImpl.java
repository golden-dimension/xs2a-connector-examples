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

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiCommonPaymentTOMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentAuthorisationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class PaymentAuthorisationSpiImpl extends AbstractAuthorisationSpi<SpiPayment> implements PaymentAuthorisationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);

    private final GeneralPaymentService paymentService;
    private final LedgersSpiCommonPaymentTOMapper ledgersSpiCommonPaymentTOMapper;
    private final AspspConsentDataService aspspConsentDataService;
    private final ScaResponseMapper scaResponseMapper;
    private final PaymentRestClient paymentRestClient;
    private final CmsPsuPisClient cmsPsuPisClient;
    private final RequestProviderService requestProviderService;

    public PaymentAuthorisationSpiImpl(GeneralAuthorisationService authorisationService,
                                       ScaMethodConverter scaMethodConverter,
                                       AuthRequestInterceptor authRequestInterceptor,
                                       AspspConsentDataService consentDataService,
                                       FeignExceptionReader feignExceptionReader,
                                       RedirectScaRestClient redirectScaRestClient,
                                       KeycloakTokenService keycloakTokenService,
                                       GeneralPaymentService paymentService,
                                       LedgersSpiCommonPaymentTOMapper ledgersSpiCommonPaymentTOMapper,
                                       AspspConsentDataService aspspConsentDataService,
                                       ScaResponseMapper scaResponseMapper,
                                       PaymentRestClient paymentRestClient, CmsPsuPisClient cmsPsuPisClient, RequestProviderService requestProviderService) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader, keycloakTokenService, redirectScaRestClient);
        this.paymentService = paymentService;
        this.ledgersSpiCommonPaymentTOMapper = ledgersSpiCommonPaymentTOMapper;
        this.aspspConsentDataService = aspspConsentDataService;
        this.scaResponseMapper = scaResponseMapper;
        this.paymentRestClient = paymentRestClient;
        this.cmsPsuPisClient = cmsPsuPisClient;
        this.requestProviderService = requestProviderService;
    }

    @Override
    protected OpTypeTO getOpType() {
        return OpTypeTO.PAYMENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiPayment businessObject) {
        logger.error("Initiate payment failed: payment ID {}", businessObject.getPaymentId());
        return new TppMessage(MessageErrorCode.PAYMENT_FAILED);
    }

    @Override
    protected String getBusinessObjectId(SpiPayment businessObject) {
        return businessObject.getPaymentId();
    }

    @Override
    protected GlobalScaResponseTO initiateBusinessObject(SpiPayment businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, String authorisationId) {

        if (businessObject.getPaymentStatus() == TransactionStatus.PATC) {
            return aspspConsentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
        }

        PaymentType paymentType = businessObject.getPaymentType();
        PaymentTO paymentTO = ledgersSpiCommonPaymentTOMapper.mapToPaymentTO(paymentType, (SpiPaymentInfo) businessObject);

        return paymentService.initiatePaymentInLedgers(businessObject, PaymentTypeTO.valueOf(paymentType.toString()), paymentTO);
    }

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiPayment businessObject, GlobalScaResponseTO scaPaymentResponseTO) {
        return !scaPaymentResponseTO.isMultilevelScaRequired() || businessObject.getPsuDataList().size() <= 1;
    }

    @Override
    protected GlobalScaResponseTO executeBusinessObject(SpiPayment businessObject) {
        SCAPaymentResponseTO paymentExecutionResponse = paymentRestClient.executePayment(businessObject.getPaymentId()).getBody();
        cmsPsuPisClient.updatePaymentStatus(businessObject.getPaymentId(), TransactionStatus.valueOf(paymentExecutionResponse.getTransactionStatus().name()), requestProviderService.getInstanceId());

        return scaResponseMapper.toGlobalScaResponse(paymentExecutionResponse);
    }

    @Override
    protected Optional<List<ScaUserDataTO>> getScaMethods(GlobalScaResponseTO sca) {
        if (sca.getScaMethods() == null) {
            return Optional.of(Collections.emptyList());
        }

        return super.getScaMethods(sca);
    }

    @Override
    public @NotNull SpiResponse<Boolean> requestTrustedBeneficiaryFlag(@NotNull SpiContextData spiContextData,
                                                                       @NotNull SpiPayment payment,
                                                                       @NotNull String authorisationId,
                                                                       @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        // TODO replace with real response from ledgers https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1263
        logger.info("Retrieving mock trusted beneficiaries flag for payment: {}", payment);
        return SpiResponse.<Boolean>builder()
                       .payload(true)
                       .build();
    }
}
