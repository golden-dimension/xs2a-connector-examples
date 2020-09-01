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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.NotSupportedPaymentTypeException;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.PaymentSpi;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.Xs2aPaymentMapper;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SinglePaymentSpiImpl extends AbstractPaymentSpi<SpiSinglePayment, SpiSinglePaymentInitiationResponse> implements SinglePaymentSpi {
    private LedgersSpiPaymentMapper paymentMapper;
    private PaymentSpi paymentSpi;
    private Xs2aPaymentMapper xs2aPaymentMapper;

    @Autowired
    public SinglePaymentSpiImpl(GeneralPaymentService paymentService, LedgersSpiPaymentMapper paymentMapper, PaymentSpi paymentSpi, Xs2aPaymentMapper xs2aPaymentMapper) {
        super(paymentService);
        this.paymentMapper = paymentMapper;
        this.paymentSpi = paymentSpi;
        this.xs2aPaymentMapper = xs2aPaymentMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment(@NotNull SpiContextData spiContextData, @NotNull SpiSinglePayment spiSinglePayment, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        try {
            SpiResponse<? extends SpiPaymentInitiationResponse> singlePayment = paymentSpi.initiatePayment(spiContextData, spiSinglePayment, spiAspspConsentDataProvider);
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder().payload(xs2aPaymentMapper.mapToSingle(singlePayment.getPayload())).build();
        } catch (NotSupportedPaymentTypeException e) {
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder().error(new TppMessage(MessageErrorCode.FORMAT_ERROR)).build();
        }
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiContextData contextData,
                                                                 @NotNull String acceptMediaType,
                                                                 @NotNull SpiSinglePayment payment,
                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        payment.setDebtorName(DEBTOR_NAME);
        return paymentService.getPaymentById(payment, aspspConsentDataProvider, paymentMapper::toSpiSinglePayment);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentConfirmationCodeValidationResponse> notifyConfirmationCodeValidation(@NotNull SpiContextData spiContextData, boolean confirmationCodeValidationResult, @NotNull SpiSinglePayment payment, boolean isCancellation, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        return super.notifyConfirmationCodeValidation(spiContextData, confirmationCodeValidationResult, payment, isCancellation, spiAspspConsentDataProvider);
    }
}
