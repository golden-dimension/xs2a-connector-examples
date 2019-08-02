package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;

import java.util.Optional;

class LedgersSpiPaymentMapperHelper {
    private static final int DEFAULT_DAY_OF_EXECUTION = 1;

    private LedgersSpiPaymentMapperHelper() {
    }

    static String mapPisExecutionRule(PisExecutionRule executionRule) {
        return Optional.ofNullable(executionRule)
                       .map(PisExecutionRule::getValue)
                       .orElse(null);
    }

    static int mapPisDayOfExecution(PisDayOfExecution execution) {
        return Optional.ofNullable(execution)
                       .map(day -> Integer.parseInt(day.getValue()))
                       .orElse(DEFAULT_DAY_OF_EXECUTION);
    }
}
