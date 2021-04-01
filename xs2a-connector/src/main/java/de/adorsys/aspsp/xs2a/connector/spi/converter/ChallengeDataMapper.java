package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.sca.ChallengeDataTO;
import de.adorsys.psd2.xs2a.core.sca.Xs2aChallengeData;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChallengeDataMapper {

	Xs2aChallengeData toChallengeData(ChallengeDataTO to);
}
