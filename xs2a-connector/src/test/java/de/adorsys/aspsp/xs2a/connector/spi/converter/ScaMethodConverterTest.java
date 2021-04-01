package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.psd2.xs2a.core.authorisation.Xs2aAuthenticationObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScaMethodConverterTest {

    private ScaMethodConverter mapper;
    private JsonReader jsonReader = new JsonReader();
    private ScaUserDataTO userData;
    private Xs2aAuthenticationObject expected;

    @BeforeEach
    void setUp() {
        userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);
        expected = jsonReader.getObjectFromFile("json/spi/converter/spi-authentication-object.json", Xs2aAuthenticationObject.class);

        mapper = Mappers.getMapper(ScaMethodConverter.class);
    }

    @Test
    void toAuthenticationObject() {
        Xs2aAuthenticationObject authenticationObject = mapper.toAuthenticationObject(userData);

        assertEquals(expected, authenticationObject);
    }

    @Test
    void toAuthenticationObject_nullValue() {
        Xs2aAuthenticationObject authenticationObject = mapper.toAuthenticationObject(null);
        assertNull(authenticationObject);
    }

    @Test
    void toAuthenticationObjectList() {
        List<Xs2aAuthenticationObject> objects = mapper.toAuthenticationObjectList(Collections.singletonList(userData));

        assertEquals(1, objects.size());
        assertEquals(expected, objects.get(0));
    }

    @Test
    void toAuthenticationObjectList_nullValue() {
        List<Xs2aAuthenticationObject> objects = mapper.toAuthenticationObjectList(null);
        assertNull(objects);
    }
}