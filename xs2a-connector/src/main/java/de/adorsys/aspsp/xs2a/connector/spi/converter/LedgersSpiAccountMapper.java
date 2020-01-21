package de.adorsys.aspsp.xs2a.connector.spi.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.ledgers.middleware.api.domain.account.*;
import de.adorsys.ledgers.middleware.api.domain.payment.AmountTO;
import de.adorsys.ledgers.middleware.api.domain.payment.RemittanceInformationStructuredTO;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Mapper(componentModel = "spring")
public abstract class LedgersSpiAccountMapper {

    public abstract List<SpiAccountDetails> toSpiAccountDetailsList(List<AccountDetailsTO> accountDetails);

    public SpiAccountDetails toSpiAccountDetails(AccountDetailsTO accountDetails) {
        return Optional.ofNullable(accountDetails)
                       .map(d -> new SpiAccountDetails(
                               d.getIban(),
                               d.getId(),
                               d.getIban(),
                               d.getBban(),
                               d.getPan(),
                               d.getMaskedPan(),
                               d.getMsisdn(),
                               d.getCurrency(),
                               d.getName(),
                               d.getProduct(),
                               SpiAccountType.valueOf(d.getAccountType().name()),
                               SpiAccountStatus.valueOf(d.getAccountStatus().name()),
                               d.getBic(),
                               d.getLinkedAccounts(),
                               SpiUsageType.valueOf(d.getUsageType().name()),
                               d.getDetails(),
                               toSpiAccountBalancesList(d.getBalances()),
                               null,
                               null))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public SpiCardAccountDetails toSpiCardAccountDetails(AccountDetailsTO accountDetails) {
        return Optional.ofNullable(accountDetails)
                       .map(d -> new SpiCardAccountDetails(
                               d.getIban(),
                               d.getId(),
                               d.getMaskedPan(),
                               d.getCurrency(),
                               d.getName(),
                               d.getProduct(),
                               SpiAccountStatus.valueOf(d.getAccountStatus().name()),
                               SpiAccountType.valueOf(d.getAccountType().name()),
                               SpiUsageType.valueOf(d.getUsageType().name()),
                               d.getDetails(),
                               new SpiAmount(Currency.getInstance("EUR"), new BigDecimal(10000)), // TODO support card account consent in Ledgers
                               toSpiAccountBalancesList(d.getBalances()),
                               null))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public abstract List<SpiTransaction> toSpiTransactions(List<TransactionTO> transactions);

    public abstract List<SpiCardTransaction> toSpiCardTransactions(List<TransactionTO> transactions);

    public SpiTransaction toSpiTransaction(TransactionTO transaction) {
        return Optional.ofNullable(transaction)
                       .map(t -> new SpiTransaction(
                               t.getTransactionId(),
                               t.getEntryReference(),
                               t.getEndToEndId(),
                               t.getMandateId(),
                               t.getCheckId(),
                               t.getCreditorId(),
                               t.getBookingDate(),
                               t.getValueDate(),
                               toSpiAmount(t.getAmount()),
                               toSpiExchangeRateList(t.getExchangeRate()),
                               t.getCreditorName(),
                               toSpiAccountReference(t.getCreditorAccount()),
                               "creditorAgent", // TODO: https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/issues/522 replace with real data.
                               t.getUltimateCreditor(),
                               t.getDebtorName(),
                               toSpiAccountReference(t.getDebtorAccount()),
                               "debtorAgent", // TODO: https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/issues/522 replace with real data.
                               t.getUltimateDebtor(),
                               t.getRemittanceInformationUnstructured(),
                               mapRemittanceInformationToString(t.getRemittanceInformationStructured()),
                               t.getPurposeCode(),
                               t.getBankTransactionCode(),
                               t.getProprietaryBankTransactionCode(),
                               null, // TODO Map proper field https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
                               new SpiAccountBalance())) // TODO: https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/issues/522 replace with real data.
                       .orElse(null);
    }  //Full manual mapping here, no extra tests necessary

    public SpiCardTransaction toSpiCardTransaction(TransactionTO transaction) {
        return Optional.ofNullable(transaction)
                       .map(t -> new SpiCardTransaction(
                               t.getTransactionId(),
                               "terminalId",
                               t.getValueDate(),
                               t.getBookingDate(),
                               toSpiAmount(t.getAmount()),
                               toSpiExchangeRateList(t.getExchangeRate()),
                               toSpiAmount(t.getAmount()),
                               toSpiAmount(t.getAmount()),
                               "markupFeePercentage",
                               t.getCreditorId(),
                               new SpiAddress("street", "buildNum", "town", "post", "EU"),
"merchantCategoryCode",
                               "493702******0836",
                               "transactionDetails",
                               false,
                               t.getProprietaryBankTransactionCode())) // TODO: https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/issues/522 replace with real data.
                       .orElse(null);
    }  //Full manual mapping here, no extra tests necessary

    public SpiAccountReference toSpiAccountReference(AccountReferenceTO reference) {
        return Optional.ofNullable(reference)
                       .map(r -> new SpiAccountReference(
                               r.getIban(),
                               r.getIban(),
                               r.getIban(),
                               r.getBban(),
                               r.getPan(),
                               r.getMaskedPan(),
                               r.getMsisdn(),
                               r.getCurrency()))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public abstract List<SpiAccountBalance> toSpiAccountBalancesList(List<AccountBalanceTO> accountBalanceTOS);

    @Mapping(source = "balanceType", target = "spiBalanceType")
    @Mapping(source = "amount", target = "spiBalanceAmount")
    public abstract SpiAccountBalance accountBalanceTOToSpiAccountBalance(AccountBalanceTO accountBalanceTO);

    public abstract List<SpiExchangeRate> toSpiExchangeRateList(List<ExchangeRateTO> exchangeRates);

    public SpiExchangeRate toSpiExchangeRate(ExchangeRateTO exchangeRate) {
        return Optional.ofNullable(exchangeRate)
                       .map(e -> new SpiExchangeRate(
                               e.getCurrencyFrom().getCurrencyCode(),
                               e.getRateFrom(),
                               e.getCurrency().getCurrencyCode(),
                               e.getRateTo(),
                               e.getRateDate(),
                               e.getRateContract()))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public SpiAmount toSpiAmount(AmountTO amount) {
        return Optional.ofNullable(amount)
                       .map(a -> new SpiAmount(a.getCurrency(), a.getAmount()))
                       .orElse(null);
    }//Full manual mapping here, no extra tests necessary

    public abstract FundsConfirmationRequestTO toFundsConfirmationTO(SpiPsuData psuData, SpiFundsConfirmationRequest spiFundsConfirmationRequest);

    public abstract AccountReferenceTO mapToAccountReferenceTO(SpiAccountReference spiAccountReference);

    private String mapRemittanceInformationToString(RemittanceInformationStructuredTO remittanceInformationStructuredTO) {
        if (remittanceInformationStructuredTO == null) {
            return null;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(remittanceInformationStructuredTO);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
