package de.adorsys.psd2.xs2a.override;

import de.adorsys.psd2.consent.api.ais.CreateAisConsentRequest;
import de.adorsys.psd2.consent.config.AisConsentRemoteUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomCmsAisConsentServiceRemote {
    @Qualifier("consentRestTemplate")
    private final RestTemplate consentRestTemplate;
    private final AisConsentRemoteUrls remoteAisConsentUrls;

    public Optional<CustomCreateAisConsentResponse> createConsent(CreateAisConsentRequest request) {
        CustomCreateAisConsentResponse createAisConsentResponse = consentRestTemplate.postForEntity(remoteAisConsentUrls.createAisConsent(), request, CustomCreateAisConsentResponse.class).getBody();
        return Optional.ofNullable(createAisConsentResponse);
    }
}
