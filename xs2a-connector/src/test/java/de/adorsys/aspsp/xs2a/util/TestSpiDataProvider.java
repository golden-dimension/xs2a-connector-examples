package de.adorsys.aspsp.xs2a.util;

import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;

import java.util.UUID;

public class TestSpiDataProvider {

    private static final UUID X_REQUEST_ID = UUID.randomUUID();
    private static final UUID INTERNAL_REQUEST_ID = UUID.randomUUID();
    private static final String AUTHORISATION = "Bearer 1111111";
    private static final String PSU_ID = "psuId";
    private static final String PSU_ID_TYPE = "psuIdType";
    private static final String PSU_CORPORATE_ID = "psuCorporateId";
    private static final String PSU_CORPORATE_ID_TYPE = "psuCorporateIdType";
    private static final String PSU_IP_ADDRESS = "psuIpAddress";
    private static final String PSU_IP_PORT = "psuIpPort";
    private static final String PSU_USER_AGENT = "psuUserAgent";
    private static final String PSU_GEO_LOCATION = "psuGeoLocation";
    private static final String PSU_ACCEPT = "psuAccept";
    private static final String PSU_ACCEPT_CHARSET = "psuAcceptCharset";
    private static final String PSU_ACCEPT_ENCODING = "psuAcceptEncoding";
    private static final String PSU_ACCEPT_LANGUAGE = "psuAcceptLanguage";
    private static final String PSU_HTTP_METHOD = "psuHttpMethod";
    private static final UUID PSU_DEVICE_ID = UUID.randomUUID();

    public static SpiContextData getSpiContextData() {
        return new SpiContextData(
                new SpiPsuData(PSU_ID, PSU_ID_TYPE, PSU_CORPORATE_ID, PSU_CORPORATE_ID_TYPE, PSU_IP_ADDRESS, PSU_IP_PORT, PSU_USER_AGENT, PSU_GEO_LOCATION, PSU_ACCEPT, PSU_ACCEPT_CHARSET, PSU_ACCEPT_ENCODING, PSU_ACCEPT_LANGUAGE, PSU_HTTP_METHOD, PSU_DEVICE_ID),
                new TppInfo(),
                X_REQUEST_ID,
                INTERNAL_REQUEST_ID,
                AUTHORISATION
        );
    }

}
