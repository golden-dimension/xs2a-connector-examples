package de.adorsys.psd2.xs2a.override;

import de.adorsys.psd2.consent.api.ais.AisAccountConsent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomCreateAisConsentResponse {
    private String consentId;
    private AisAccountConsent aisAccountConsent;
}
