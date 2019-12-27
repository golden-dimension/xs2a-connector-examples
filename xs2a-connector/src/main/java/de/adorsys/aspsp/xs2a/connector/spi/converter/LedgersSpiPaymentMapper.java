package de.adorsys.aspsp.xs2a.connector.spi.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.ledgers.middleware.api.domain.payment.*;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.psd2.models.*;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {LedgersSpiAccountMapper.class, ChallengeDataMapper.class, AddressMapper.class},
        imports = {LedgersSpiPaymentMapperHelper.class, ScaMethodUtils.class})
public abstract class LedgersSpiPaymentMapper {

    private LedgersSpiAccountMapper accountMapper = Mappers.getMapper(LedgersSpiAccountMapper.class);
    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "requestedExecutionTime", expression = "java(toTime(payment.getRequestedExecutionTime()))")
    @Mapping(target = "paymentProduct", expression = "java(toPaymentProduct(payment.getPaymentProduct()))")
    public abstract SinglePaymentTO toSinglePaymentTO(SpiSinglePayment payment);

    @Mapping(target = "executionRule", expression = "java(LedgersSpiPaymentMapperHelper.mapPisExecutionRule(payment.getExecutionRule()))")
    @Mapping(target = "dayOfExecution", expression = "java(LedgersSpiPaymentMapperHelper.mapPisDayOfExecution(payment.getDayOfExecution()))")
    @Mapping(target = "frequency", expression = "java(LedgersSpiPaymentMapperHelper.mapFrequencyCode(payment.getFrequency()))")
    // TODO Remove it https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
    public abstract PeriodicPaymentTO toPeriodicPaymentTO(SpiPeriodicPayment payment);

    public PaymentTO toCommonPaymentTO(SpiPaymentInfo spiPaymentInfo) {
        PaymentTO paymentTO = new PaymentTO();
        PaymentType paymentType = spiPaymentInfo.getPaymentType();

        return paymentTO;
    }

    public PaymentTO toPaymentTO_Single(SpiPaymentInfo spiPaymentInfo) {
        return Optional.ofNullable(spiPaymentInfo.getPaymentData())
                       .filter(ArrayUtils::isNotEmpty)
                       .map(paymentData -> convert(paymentData, PaymentInitiationJson.class))
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().toString()));
                           paymentTO.setPaymentProduct(spiPaymentInfo.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setDebtorName(payment.getUltimateDebtor());
                           paymentTO.setTargets(Collections.singletonList(mapToPaymentTargetTO(payment, spiPaymentInfo)));
                           return paymentTO;

                       })
                       .orElse(null);
    }

    public PaymentTO toPaymentTO_Bulk(SpiPaymentInfo spiPaymentInfo) {
        return Optional.ofNullable(spiPaymentInfo.getPaymentData())
                       .filter(ArrayUtils::isNotEmpty)
                       .map(paymentData -> convert(paymentData, BulkPaymentInitiationJson.class))
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().toString()));
                           paymentTO.setPaymentProduct(spiPaymentInfo.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setBatchBookingPreferred(payment.getBatchBookingPreferred());
                           paymentTO.setRequestedExecutionDate(payment.getRequestedExecutionDate());
                           paymentTO.setRequestedExecutionTime(Optional.ofNullable(payment.getRequestedExecutionTime()).map(OffsetDateTime::toLocalTime).orElse(null));
                           paymentTO.setTargets(payment.getPayments().stream()
                                                        .map(bulk -> mapToPaymentTargetTO(bulk, spiPaymentInfo))
                                                        .collect(Collectors.toList()));
                           return paymentTO;
                       })
                       .orElse(null);

    }

    public PaymentTO toPaymentTO_Periodic(SpiPaymentInfo spiPaymentInfo) {
        return Optional.ofNullable(spiPaymentInfo.getPaymentData())
                       .filter(ArrayUtils::isNotEmpty)
                       .map(paymentData -> convert(paymentData, PeriodicPaymentInitiationJson.class))
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().toString()));
                           paymentTO.setPaymentProduct(spiPaymentInfo.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setStartDate(payment.getStartDate());
                           paymentTO.setEndDate(payment.getEndDate());
                           paymentTO.setExecutionRule(payment.getExecutionRule().toString());
                           paymentTO.setFrequency(FrequencyCodeTO.valueOf(payment.getFrequency().toString()));
                           paymentTO.setDayOfExecution(Integer.valueOf(payment.getDayOfExecution().toString()));
                           paymentTO.setDebtorName(payment.getUltimateDebtor());
                           paymentTO.setTargets(Collections.singletonList(mapToPaymentTargetTO(payment, spiPaymentInfo)));

                           return paymentTO;

                       })
                       .orElse(null);
    }

    private <T> T convert(byte[] paymentData, Class<T> tClass) {
        try {
            return objectMapper.readValue(paymentData, tClass);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private AccountReferenceTO mapToAccountReferenceTO(AccountReference accountReference) {
        if (accountReference == null) {
            return null;
        }
        AccountReferenceTO accountReferenceTO = new AccountReferenceTO();

        accountReferenceTO.setIban(accountReference.getIban());
        accountReferenceTO.setBban(accountReference.getBban());
        accountReferenceTO.setPan(accountReference.getPan());
        accountReferenceTO.setMaskedPan(accountReference.getMaskedPan());
        accountReferenceTO.setMsisdn(accountReference.getMsisdn());
        accountReferenceTO.setCurrency(Currency.getInstance(accountReference.getCurrency()));

        return accountReferenceTO;
    }

    private PaymentTargetTO mapToPaymentTargetTO(PeriodicPaymentInitiationJson payment, SpiPaymentInfo spiPaymentInfo) {
        if (payment == null) {
            return null;
        }

        PaymentTargetTO paymentTargetTO = new PaymentTargetTO();

        paymentTargetTO.setPaymentId(spiPaymentInfo.getPaymentId());
        paymentTargetTO.setEndToEndIdentification(payment.getEndToEndIdentification());
        paymentTargetTO.setInstructedAmount(mapToAmountTO(payment.getInstructedAmount()));
        paymentTargetTO.setCreditorAccount(mapToAccountReferenceTO(payment.getCreditorAccount()));
        paymentTargetTO.setCreditorAgent(payment.getCreditorAgent());
        paymentTargetTO.setCreditorName(payment.getCreditorName());
        paymentTargetTO.setCreditorAddress(mapToAddressTO(payment.getCreditorAddress()));
        paymentTargetTO.setPurposeCode(Optional.ofNullable(payment.getPurposeCode()).map(PurposeCode::toString).map(PurposeCodeTO::valueOf).orElse(null));
        paymentTargetTO.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        paymentTargetTO.setRemittanceInformationStructured(mapToRemittanceInformationStructuredTO(payment.getRemittanceInformationStructured()));

        return paymentTargetTO;
    }

    private PaymentTargetTO mapToPaymentTargetTO(PaymentInitiationBulkElementJson payment, SpiPaymentInfo spiPaymentInfo) {
        if (payment == null) {
            return null;
        }

        PaymentTargetTO paymentTargetTO = new PaymentTargetTO();

        paymentTargetTO.setPaymentId(spiPaymentInfo.getPaymentId());
        paymentTargetTO.setEndToEndIdentification(payment.getEndToEndIdentification());
        paymentTargetTO.setInstructedAmount(mapToAmountTO(payment.getInstructedAmount()));
        paymentTargetTO.setCreditorAccount(mapToAccountReferenceTO(payment.getCreditorAccount()));
        paymentTargetTO.setCreditorAgent(payment.getCreditorAgent());
        paymentTargetTO.setCreditorName(payment.getCreditorName());
        paymentTargetTO.setCreditorAddress(mapToAddressTO(payment.getCreditorAddress()));
        paymentTargetTO.setPurposeCode(Optional.ofNullable(payment.getPurposeCode()).map(PurposeCode::toString).map(PurposeCodeTO::valueOf).orElse(null));
        paymentTargetTO.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        paymentTargetTO.setRemittanceInformationStructured(mapToRemittanceInformationStructuredTO(payment.getRemittanceInformationStructured()));

        return paymentTargetTO;
    }

    private PaymentTargetTO mapToPaymentTargetTO(PaymentInitiationJson payment, SpiPaymentInfo spiPaymentInfo) {
        if (payment == null) {
            return null;
        }

        PaymentTargetTO paymentTargetTO = new PaymentTargetTO();

        paymentTargetTO.setPaymentId(spiPaymentInfo.getPaymentId());
        paymentTargetTO.setEndToEndIdentification(payment.getEndToEndIdentification());
        paymentTargetTO.setInstructedAmount(mapToAmountTO(payment.getInstructedAmount()));
        paymentTargetTO.setCreditorAccount(mapToAccountReferenceTO(payment.getCreditorAccount()));
        paymentTargetTO.setCreditorAgent(payment.getCreditorAgent());
        paymentTargetTO.setCreditorName(payment.getCreditorName());
        paymentTargetTO.setCreditorAddress(mapToAddressTO(payment.getCreditorAddress()));
        paymentTargetTO.setPurposeCode(Optional.ofNullable(payment.getPurposeCode()).map(PurposeCode::toString).map(PurposeCodeTO::valueOf).orElse(null));
        paymentTargetTO.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        paymentTargetTO.setRemittanceInformationStructured(mapToRemittanceInformationStructuredTO(payment.getRemittanceInformationStructured()));

        return paymentTargetTO;
    }

    protected RemittanceInformationStructuredTO mapToRemittanceInformationStructuredTO(RemittanceInformationStructured remittanceInformationStructured) {
        if (remittanceInformationStructured == null) {
            return null;
        }

        RemittanceInformationStructuredTO remittanceInformationStructuredTO = new RemittanceInformationStructuredTO();

        remittanceInformationStructuredTO.setReference(remittanceInformationStructured.getReference());
        remittanceInformationStructuredTO.setReferenceType(remittanceInformationStructured.getReferenceType());
        remittanceInformationStructuredTO.setReferenceIssuer(remittanceInformationStructured.getReferenceIssuer());

        return remittanceInformationStructuredTO;
    }

    private AmountTO mapToAmountTO(Amount amount) {
        if (amount == null) {
            return null;
        }

        AmountTO amountTO = new AmountTO();
        amountTO.setCurrency(Currency.getInstance(amount.getCurrency()));
        amountTO.setAmount(BigDecimal.valueOf(Double.parseDouble(amount.getAmount())));

        return amountTO;
    }

    private AddressTO mapToAddressTO(Address address) {
        if (address == null) {
            return null;
        }

        AddressTO addressTO = new AddressTO();

        addressTO.setStreet(address.getStreetName());
        addressTO.setBuildingNumber(address.getBuildingNumber());
        addressTO.setCity(address.getTownName());
        addressTO.setPostalCode(address.getPostCode());
        addressTO.setCountry(address.getCountry());
//        addressTO.setLine1();
//        addressTO.setLine2();

        return addressTO;
    }


    public abstract BulkPaymentTO toBulkPaymentTO(SpiBulkPayment payment);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiSinglePaymentInitiationResponse toSpiSingleResponse(SinglePaymentTO payment);

    @Mapping(target = "scaMethods", expression = "java(ScaMethodUtils.toScaMethods(response.getScaMethods()))")
    @Mapping(target = "chosenScaMethod", expression = "java(ScaMethodUtils.toScaMethod(response.getChosenScaMethod()))")
    public abstract SpiSinglePaymentInitiationResponse toSpiSingleResponse(SCAPaymentResponseTO response);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiPeriodicPaymentInitiationResponse toSpiPeriodicResponse(PeriodicPaymentTO payment);

    @Mapping(target = "scaMethods", expression = "java(ScaMethodUtils.toScaMethods(response.getScaMethods()))")
    @Mapping(target = "chosenScaMethod", expression = "java(ScaMethodUtils.toScaMethod(response.getChosenScaMethod()))")
    public abstract SpiPeriodicPaymentInitiationResponse toSpiPeriodicResponse(SCAPaymentResponseTO response);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiBulkPaymentInitiationResponse toSpiBulkResponse(BulkPaymentTO payment);

    @Mapping(target = "scaMethods", expression = "java(ScaMethodUtils.toScaMethods(response.getScaMethods()))")
    @Mapping(target = "chosenScaMethod", expression = "java(ScaMethodUtils.toScaMethod(response.getChosenScaMethod()))")
    public abstract SpiBulkPaymentInitiationResponse toSpiBulkResponse(SCAPaymentResponseTO response);

    public SpiSinglePayment toSpiSinglePayment(SinglePaymentTO payment) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(payment.getPaymentProduct().getValue());
        spiPayment.setPaymentId(payment.getPaymentId());
        spiPayment.setEndToEndIdentification(payment.getEndToEndIdentification());
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(payment.getDebtorAccount()));
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(payment.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(payment.getCreditorAccount()));
        spiPayment.setCreditorAgent(payment.getCreditorAgent());
        spiPayment.setCreditorName(payment.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(payment.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        spiPayment.setPaymentStatus(TransactionStatus.valueOf(payment.getPaymentStatus().name()));
        spiPayment.setRequestedExecutionDate(payment.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(Optional.ofNullable(payment.getRequestedExecutionDate())
                                                     .map(d -> toDateTime(d, payment.getRequestedExecutionTime()))
                                                     .orElse(null));
        return spiPayment;
    } //Direct mapping no need for testing

    public SpiPeriodicPayment mapToSpiPeriodicPayment(PeriodicPaymentTO payment) {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment(payment.getPaymentProduct().getValue());
        spiPayment.setPaymentId(payment.getPaymentId());
        spiPayment.setEndToEndIdentification(payment.getEndToEndIdentification());
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(payment.getDebtorAccount()));
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(payment.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(payment.getCreditorAccount()));
        spiPayment.setCreditorAgent(payment.getCreditorAgent());
        spiPayment.setCreditorName(payment.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(payment.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        spiPayment.setPaymentStatus(TransactionStatus.valueOf(payment.getPaymentStatus().name()));
        spiPayment.setRequestedExecutionDate(payment.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(toDateTime(payment.getRequestedExecutionDate(), payment.getRequestedExecutionTime()));
        spiPayment.setStartDate(payment.getStartDate());
        spiPayment.setEndDate(payment.getEndDate());
        Optional<PisExecutionRule> pisExecutionRule = PisExecutionRule.getByValue(payment.getExecutionRule());
        pisExecutionRule.ifPresent(spiPayment::setExecutionRule);
        spiPayment.setFrequency(FrequencyCode.valueOf(payment.getFrequency().name()));
        spiPayment.setDayOfExecution(PisDayOfExecution.fromValue(String.valueOf(payment.getDayOfExecution())));
        return spiPayment;
    } //Direct mapping no need for testing

    public SpiBulkPayment mapToSpiBulkPayment(BulkPaymentTO payment) {
        return Optional.ofNullable(payment)
                       .map(p -> {
                           SpiBulkPayment spiBulkPayment = new SpiBulkPayment();
                           spiBulkPayment.setPaymentId(p.getPaymentId());
                           spiBulkPayment.setBatchBookingPreferred(p.getBatchBookingPreferred());
                           spiBulkPayment.setDebtorAccount(accountMapper.toSpiAccountReference(p.getDebtorAccount()));
                           spiBulkPayment.setRequestedExecutionDate(p.getRequestedExecutionDate());
                           spiBulkPayment.setPaymentStatus(TransactionStatus.valueOf(p.getPaymentStatus().name()));
                           spiBulkPayment.setPayments(toSpiSinglePaymentsList(p.getPayments()));
                           spiBulkPayment.setPaymentProduct(p.getPaymentProduct().getValue());
                           return spiBulkPayment;
                       }).orElse(null);
    }

    public abstract List<SpiSinglePayment> toSpiSinglePaymentsList(List<SinglePaymentTO> payments);

    public LocalTime toTime(OffsetDateTime time) {
        return Optional.ofNullable(time)
                       .map(OffsetDateTime::toLocalTime)
                       .orElse(null);
    } //Direct mapping no need for testing

    public OffsetDateTime toDateTime(LocalDate date, LocalTime time) {
        return Optional.ofNullable(date)
                       .map(d -> LocalDateTime.of(d, Optional.ofNullable(time)
                                                             .orElse(LocalTime.ofSecondOfDay(0)))
                                         .atOffset(ZoneOffset.UTC))
                       .orElse(null);
    } //Direct mapping no need for testing

    private SpiAddress toSpiAddress(AddressTO address) {
        return Optional.ofNullable(address)
                       .map(a -> new SpiAddress(
                               a.getStreet(),
                               a.getBuildingNumber(),
                               a.getCity(),
                               a.getPostalCode(),
                               a.getCountry()))
                       .orElse(null);
    } //Direct mapping no need for testing

    public SpiPaymentCancellationResponse toSpiPaymentCancellationResponse(SCAPaymentResponseTO response) {
        return Optional.ofNullable(response)
                       .map(t -> {
                           SpiPaymentCancellationResponse cancellation = new SpiPaymentCancellationResponse();
                           cancellation.setCancellationAuthorisationMandated(needAuthorization(response));
                           cancellation.setTransactionStatus(TransactionStatus.valueOf(response.getTransactionStatus().name()));
                           return cancellation;
                       }).orElseGet(SpiPaymentCancellationResponse::new);
    }//Direct mapping no testing necessary

    PaymentProductTO toPaymentProduct(String paymentProduct) {
        if (paymentProduct == null) {
            return null;
        }
        return PaymentProductTO.getByValue(paymentProduct)
                       .orElse(null);
    }

    /*
     * How do we know if a payment or a cancellation needs authorization.
     *
     * At initiation the SCAStatus shall be set to {@link ScaStatusTO#EXEMPTED}
     */
    private boolean needAuthorization(SCAPaymentResponseTO response) {
        return !ScaStatusTO.EXEMPTED.equals(response.getScaStatus());
    }
}
