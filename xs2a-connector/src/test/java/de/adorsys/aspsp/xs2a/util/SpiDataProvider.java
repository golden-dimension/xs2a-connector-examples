package de.adorsys.aspsp.xs2a.util;

import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;

import java.util.UUID;

public class SpiDataProvider {

    private static final UUID X_REQUEST_ID = UUID.randomUUID();
    private static final String AUTHORISATION = "Bearer 1111111";

    public static SpiContextData getSpiContextData() {
        return new SpiContextData(
                new SpiPsuData("psuId", "psuIdType", "psuCorporateId", "psuCorporateIdType", "psuIpAddress"),
                new TppInfo(),
                X_REQUEST_ID,
                UUID.randomUUID(),
                AUTHORISATION
        );
    }

}
