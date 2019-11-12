package de.adorsys.aspsp.xs2a.embedded.connector.cms;

import de.adorsys.psd2.consent.psu.api.CmsPsuPisService;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CmsPsuPisClientEmbeddedImplTest {
    private static final String PAYMENT_ID = "some payment id";
    private static final TransactionStatus TRANSACTION_STATUS = TransactionStatus.ACSP;
    private static final String INSTANCE_ID = "UNDEFINED";

    @Mock
    private CmsPsuPisService cmsPsuPisService;

    @InjectMocks
    private CmsPsuPisClientEmbeddedImpl cmsPsuPisClientEmbedded;

    @Test
    public void updatePaymentStatus_shouldExecuteCmsMethod() {
        // When
        cmsPsuPisClientEmbedded.updatePaymentStatus(PAYMENT_ID, TRANSACTION_STATUS, INSTANCE_ID);

        // Then
        verify(cmsPsuPisService).updatePaymentStatus(PAYMENT_ID, TRANSACTION_STATUS, INSTANCE_ID);
    }
}