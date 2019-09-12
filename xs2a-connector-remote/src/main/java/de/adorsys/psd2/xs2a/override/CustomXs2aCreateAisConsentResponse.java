package de.adorsys.psd2.xs2a.override;

import de.adorsys.psd2.xs2a.domain.consent.AccountConsent;
import lombok.Value;

@Value
public class CustomXs2aCreateAisConsentResponse {
    private String consentId;
    private AccountConsent accountConsent;
}
