package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CommonPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiPsuData PSU_ID_DATA = new SpiPsuData("1", "2", "3", "4", "5");
    private static final SpiContextData SPI_CONTEXT_DATA = new SpiContextData(PSU_ID_DATA, new TppInfo(), UUID.randomUUID(), UUID.randomUUID());

    @InjectMocks
    private CommonPaymentSpiImpl commonPaymentSpi;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;

    @Test
    public void executePaymentWithoutSca() {
        SpiResponse<SpiPaymentExecutionResponse> response = commonPaymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, new SpiPaymentInfo(PAYMENT_PRODUCT), spiAspspConsentDataProvider);

        assertTrue(response.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, response.getErrors().get(0).getErrorCode());
    }

    @Test
    public void verifyScaAuthorisationAndExecutePayment() {
        SpiResponse<SpiPaymentExecutionResponse> response = commonPaymentSpi.verifyScaAuthorisationAndExecutePayment(SPI_CONTEXT_DATA, new SpiScaConfirmation(), new SpiPaymentInfo(PAYMENT_PRODUCT), spiAspspConsentDataProvider);

        assertTrue(response.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, response.getErrors().get(0).getErrorCode());
    }
}