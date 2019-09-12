package de.adorsys.psd2.xs2a.override;

import de.adorsys.psd2.consent.api.ais.CreateAisConsentRequest;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.domain.consent.AccountConsent;
import de.adorsys.psd2.xs2a.domain.consent.CreateConsentReq;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.service.mapper.consent.Xs2aAisConsentMapper;
import de.adorsys.psd2.xs2a.service.profile.FrequencyPerDateCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Primary
@Service
public class CustomXs2aAisConsentService {
    private final CustomCmsAisConsentServiceRemote customCmsAisConsentServiceRemote;
    private final Xs2aAisConsentMapper aisConsentMapper;
    private final FrequencyPerDateCalculationService frequencyPerDateCalculationService;
    private final RequestProviderService requestProviderService;

    public CustomXs2aAisConsentService(Xs2aAisConsentMapper aisConsentMapper,
                                       FrequencyPerDateCalculationService frequencyPerDateCalculationService, RequestProviderService requestProviderService,
                                       CustomCmsAisConsentServiceRemote customCmsAisConsentServiceRemote) {
        this.aisConsentMapper = aisConsentMapper;
        this.frequencyPerDateCalculationService = frequencyPerDateCalculationService;
        this.requestProviderService = requestProviderService;
        this.customCmsAisConsentServiceRemote = customCmsAisConsentServiceRemote;
    }

    public CustomXs2aCreateAisConsentResponse customCreateConsent(CreateConsentReq request, PsuIdData psuData, TppInfo tppInfo) {
        int allowedFrequencyPerDay = frequencyPerDateCalculationService.getMinFrequencyPerDay(request.getFrequencyPerDay());
        CreateAisConsentRequest createAisConsentRequest = aisConsentMapper.mapToCreateAisConsentRequest(request, psuData, tppInfo, allowedFrequencyPerDay);
        Optional<CustomCreateAisConsentResponse> response = customCmsAisConsentServiceRemote.createConsent(createAisConsentRequest);

        if (!response.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}]. Consent cannot be created, because can't save to cms DB",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId());
            return null;
        }

        AccountConsent accountConsent = aisConsentMapper.mapToAccountConsent(response.get().getAisAccountConsent());
        return new CustomXs2aCreateAisConsentResponse(response.get().getConsentId(), accountConsent);
    }
}
