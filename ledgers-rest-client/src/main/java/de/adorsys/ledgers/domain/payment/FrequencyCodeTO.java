package de.adorsys.ledgers.domain.payment;

import de.adorsys.ledgers.exception.FrequencyCodeInvalidMiddlewareException;

public enum FrequencyCodeTO {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    EVERYTWOWEEKS("EveryTwoWeeks"),
    MONTHLY("Monthly"),
    EVERYTWOMONTHS("EveryTwoMonths"),
    QUARTERLY("Quarterly"),
    SEMIANNUAL("SemiAnnual"),
    ANNUAL("Annual");

    private String value;

    FrequencyCodeTO(String value) {
        this.value = value;
    }


    public static FrequencyCodeTO fromValue(String text) throws FrequencyCodeInvalidMiddlewareException {
        for (FrequencyCodeTO b : FrequencyCodeTO.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new FrequencyCodeInvalidMiddlewareException(text);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }


}
