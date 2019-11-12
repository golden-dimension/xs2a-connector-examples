package de.adorsys.aspsp.xs2a.embedded.connector.cms;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.psd2.consent.psu.api.CmsPsuPisService;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CmsPsuPisClientEmbeddedImpl implements CmsPsuPisClient {
    private final CmsPsuPisService cmsPsuPisService;

    @Override
    public void updatePaymentStatus(String paymentId, TransactionStatus transactionStatus, String instanceId) {
        cmsPsuPisService.updatePaymentStatus(paymentId, transactionStatus, instanceId);
    }
}
