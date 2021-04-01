package de.adorsys.aspsp.xs2a.connector.cms;

import de.adorsys.psd2.xs2a.core.pis.Xs2aTransactionStatus;

public interface CmsPsuPisClient {
    /**
     * Updates payment status directly in the CMS
     *
     * @param paymentId         internal payment ID
     * @param transactionStatus new transaction status
     * @param instanceId        ID of particular CMS service instance
     */
    void updatePaymentStatus(String paymentId, Xs2aTransactionStatus transactionStatus, String instanceId);
}
