package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;

@Service
@RequiredArgsConstructor
public class CmsPaymentStatusUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(CmsPaymentStatusUpdateService.class);

    private final CmsPsuPisClient cmsPsuPisClient;
    private final AspspConsentDataService consentDataService;
    private final RequestProviderService requestProviderService;

    public void updatePaymentStatus(String paymentId, SpiAspspConsentDataProvider aspspConsentDataProvider) {
        GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
        TransactionStatus transactionStatus = getTransactionStatus(sca.getScaStatus());
        cmsPsuPisClient.updatePaymentStatus(paymentId, transactionStatus, requestProviderService.getInstanceId());
    }

    private TransactionStatus getTransactionStatus(ScaStatusTO scaStatus) {
        if (EnumSet.of(PSUIDENTIFIED, EXEMPTED).contains(scaStatus)) {
            return TransactionStatus.ACCP;
        } else if (scaStatus == PSUAUTHENTICATED) {
            return TransactionStatus.ACTC;
        } else {
            return TransactionStatus.RCVD;
        }
    }
}
