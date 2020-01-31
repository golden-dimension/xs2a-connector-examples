package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalSingle implements PaymentInternal<SpiSinglePayment> {
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;

    @Override
    public SCAPaymentResponseTO initiatePaymentInternal(SpiSinglePayment payment, byte[] initialAspspConsentData) {
        PaymentTO request = paymentMapper.mapToPaymentTO(payment);
        return paymentService.initiatePaymentInternal(payment, initialAspspConsentData, PaymentTypeTO.SINGLE, request);
    }
}
