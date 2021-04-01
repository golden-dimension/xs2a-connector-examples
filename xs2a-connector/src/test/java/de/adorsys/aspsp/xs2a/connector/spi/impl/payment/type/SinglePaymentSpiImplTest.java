package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.PaymentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.core.pis.Xs2aTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class SinglePaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String JSON_ACCEPT_MEDIA_TYPE = "application/json";
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI for this payment";

    @InjectMocks
    private SinglePaymentSpiImpl singlePaymentSpi;

    @Mock
    private GeneralPaymentService paymentService;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AspspConsentDataService aspspConsentDataService;
    @Mock
    private PaymentAuthConfirmationCodeService paymentAuthConfirmationCodeService;

    @Spy
    private LedgersSpiPaymentMapper paymentMapper = new LedgersSpiPaymentMapper(Mappers.getMapper(LedgersSpiAccountMapper.class));
    private SpiSinglePayment payment;

    @BeforeEach
    void setUp() {
        payment = new SpiSinglePayment(PAYMENT_PRODUCT);
        payment.setPaymentId(PAYMENT_ID);
        payment.setPaymentStatus(Xs2aTransactionStatus.RCVD);
    }

    @Test
    void getPaymentById() {
        when(paymentService.getPaymentById(eq(payment), eq(spiAspspConsentDataProvider), any()))
                .thenReturn(SpiResponse.<SpiSinglePayment>builder()
                                    .payload(new SpiSinglePayment(PAYMENT_PRODUCT))
                                    .build());

        singlePaymentSpi.getPaymentById(SPI_CONTEXT_DATA, JSON_ACCEPT_MEDIA_TYPE, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).getPaymentById(eq(payment), eq(spiAspspConsentDataProvider), any());
    }

    @Test
    void getPaymentStatusById() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(paymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, JSON_ACCEPT_MEDIA_TYPE, PAYMENT_ID, Xs2aTransactionStatus.RCVD, CONSENT_DATA_BYTES))
                .thenReturn(SpiResponse.<SpiGetPaymentStatusResponse>builder()
                                    .payload(new SpiGetPaymentStatusResponse(Xs2aTransactionStatus.RCVD, false, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null, PSU_MESSAGE))
                                    .build());

        singlePaymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, JSON_ACCEPT_MEDIA_TYPE, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).getPaymentStatusById(PaymentTypeTO.SINGLE, JSON_ACCEPT_MEDIA_TYPE, PAYMENT_ID, Xs2aTransactionStatus.RCVD, CONSENT_DATA_BYTES);
    }

    @Test
    void executePaymentWithoutSca() {
        when(paymentService.executePaymentWithoutSca(spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(Xs2aTransactionStatus.RCVD))
                                    .build());

        singlePaymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).executePaymentWithoutSca(spiAspspConsentDataProvider);
    }

    @Test
    void verifyScaAuthorisationAndExecutePayment() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        when(paymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(Xs2aTransactionStatus.RCVD))
                                    .build());

        singlePaymentSpi.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(SPI_CONTEXT_DATA, spiScaConfirmation, payment, spiAspspConsentDataProvider);

        verify(paymentService).verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider);
    }

    @Test
    void initiatePayment_emptyConsentData() {
        ArgumentCaptor<SpiSinglePaymentInitiationResponse> spiSinglePaymentInitiationResponseCaptor
                = ArgumentCaptor.forClass(SpiSinglePaymentInitiationResponse.class);

        Set<SpiAccountReference> spiAccountReferences = new HashSet<>(Collections.singleton(payment.getDebtorAccount()));
        when(paymentService.firstCallInstantiatingPayment(eq(PaymentTypeTO.SINGLE), eq(payment),
                                                          eq(spiAspspConsentDataProvider), spiSinglePaymentInitiationResponseCaptor.capture(), eq(SPI_CONTEXT_DATA.getPsuData()), eq(spiAccountReferences)))
                .thenReturn(SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                                    .payload(new SpiSinglePaymentInitiationResponse())
                                    .build());

        singlePaymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.SINGLE), eq(payment),
                                                                       eq(spiAspspConsentDataProvider), any(SpiSinglePaymentInitiationResponse.class), eq(SPI_CONTEXT_DATA.getPsuData()), eq(spiAccountReferences));
        assertNull(spiSinglePaymentInitiationResponseCaptor.getValue().getPaymentId());
    }

}