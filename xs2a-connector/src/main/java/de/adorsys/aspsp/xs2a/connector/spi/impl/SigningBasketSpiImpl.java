/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAvailableScaMethodsResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiOtpFormat;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiSigningBasketTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.sb.SpiInitiateSigningBasketResponse;
import de.adorsys.psd2.xs2a.spi.domain.sb.SpiSigningBasket;
import de.adorsys.psd2.xs2a.spi.service.SigningBasketSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class SigningBasketSpiImpl implements SigningBasketSpi {
    @Override
    public SpiResponse<SpiInitiateSigningBasketResponse> initiateSigningBasket(@NotNull SpiContextData spiContextData, SpiSigningBasket spiSigningBasket, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        SpiAuthenticationObject spiAuthenticationObject = new SpiAuthenticationObject();
        spiAuthenticationObject.setAuthenticationMethodId("J1e-h-XFTAcpnumH2eJaKM");
        spiAuthenticationObject.setName("EMAIL");
        spiAuthenticationObject.setAuthenticationVersion("version");
        spiAuthenticationObject.setAuthenticationType("type");
        spiAuthenticationObject.setExplanation("explanation");
        spiAuthenticationObject.setDecoupled(false);

        SpiChallengeData spiChallengeData = new SpiChallengeData("data".getBytes(), Collections.singletonList("data"), "link", 5, SpiOtpFormat.CHARACTERS, "additional info");
        SpiInitiateSigningBasketResponse spiInitiateSigningBasketResponse = new SpiInitiateSigningBasketResponse(SpiSigningBasketTransactionStatus.RCVD,
                                                                                                                 spiSigningBasket.getBasketId(),
                                                                                                                 Collections.singletonList(spiAuthenticationObject),
                                                                                                                 spiAuthenticationObject,
                                                                                                                 spiChallengeData,
                                                                                                                 false,
                                                                                                                 "psu message",
                                                                                                                 Collections.emptyList());

        return SpiResponse.<SpiInitiateSigningBasketResponse>builder()
                       .payload(spiInitiateSigningBasketResponse)
                       .build();
    }

    @Override
    public SpiResponse<SpiPsuAuthorisationResponse> authorisePsu(@NotNull SpiContextData spiContextData, @NotNull String s, @NotNull SpiPsuData spiPsuData, String s1, SpiSigningBasket spiSigningBasket, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        return null;
    }

    @Override
    public SpiResponse<SpiAvailableScaMethodsResponse> requestAvailableScaMethods(@NotNull SpiContextData spiContextData, SpiSigningBasket spiSigningBasket, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        return null;
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData spiContextData, @NotNull String s, @NotNull SpiSigningBasket spiSigningBasket, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        return null;
    }

    @Override
    public @NotNull SpiResponse<Boolean> requestTrustedBeneficiaryFlag(@NotNull SpiContextData spiContextData, @NotNull SpiSigningBasket spiSigningBasket, @NotNull String s, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        return null;
    }
}
