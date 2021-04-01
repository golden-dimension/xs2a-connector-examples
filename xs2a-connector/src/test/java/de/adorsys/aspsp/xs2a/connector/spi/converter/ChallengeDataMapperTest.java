package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.sca.ChallengeDataTO;
import de.adorsys.psd2.xs2a.core.sca.Xs2aChallengeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ChallengeDataMapperImpl.class})
class ChallengeDataMapperTest {

    @Autowired
    private ChallengeDataMapper challengeDataMapper;
    private JsonReader jsonReader = new JsonReader();

    @Test
    void toChallengeDataWithRealData() {
        ChallengeDataTO inputData = jsonReader.getObjectFromFile("json/mappers/challenge-data-to.json", ChallengeDataTO.class);
        inputData.setImage("image".getBytes());
        Xs2aChallengeData actualResult = challengeDataMapper.toChallengeData(inputData);
        Xs2aChallengeData expectedResult = jsonReader.getObjectFromFile("json/mappers/challenge-data.json", Xs2aChallengeData.class);
        expectedResult.setImage("image".getBytes());
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toChallengeDataWithNull() {
        Xs2aChallengeData actualResult = challengeDataMapper.toChallengeData(null);
        assertNull(actualResult);
    }
}