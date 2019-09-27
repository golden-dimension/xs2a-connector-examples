/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse.VoidResponse;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import feign.FeignException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;

@Component
public class AisConsentSpiImpl extends AbstractAuthorisationSpi<SpiAccountConsent, SCAConsentResponseTO> implements AisConsentSpi {
    private static final Logger logger = LoggerFactory.getLogger(AisConsentSpiImpl.class);
    private static final String USER_LOGIN = "{userLogin}";
    private static final String CONSENT_ID = "{consentId}";
    private static final String AUTH_ID = "{authorizationId}";
    private static final String TAN = "{tan}";
    private static final String DECOUPLED_USR_MSG = "Please check your app to continue... %s";

    private static final String SCA_STATUS_LOG = "SCA status is {}";

    private final ConsentRestClient consentRestClient;
    private final TokenStorageService tokenStorageService;
    private final AisConsentMapper aisConsentMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final ScaLoginMapper scaLoginMapper;
    private final FeignExceptionReader feignExceptionReader;

    @Value("${online-banking.url}")
    private String onlineBankingUrl;

    public AisConsentSpiImpl(ConsentRestClient consentRestClient, TokenStorageService tokenStorageService,
                             AisConsentMapper aisConsentMapper, AuthRequestInterceptor authRequestInterceptor,
                             AspspConsentDataService consentDataService, GeneralAuthorisationService authorisationService,
                             ScaMethodConverter scaMethodConverter, ScaLoginMapper scaLoginMapper, FeignExceptionReader feignExceptionReader) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader);
        this.consentRestClient = consentRestClient;
        this.tokenStorageService = tokenStorageService;
        this.aisConsentMapper = aisConsentMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.scaLoginMapper = scaLoginMapper;
        this.feignExceptionReader = feignExceptionReader;
    }

    /*
     * Initiates an ais consent. Initiation request assumes that the psu id at least
     * identified. THis is, we read a {@link SCAResponseTO} object from the {@link AspspConsentData} input.
     */
    @Override
    public SpiResponse<SpiInitiateAisConsentResponse> initiateAisConsent(@NotNull SpiContextData contextData,
                                                                         SpiAccountConsent accountConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] initialAspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        if (ArrayUtils.isEmpty(initialAspspConsentData)) {
            return firstCallInstantiatingConsent(accountConsent, aspspConsentDataProvider, new SpiInitiateAisConsentResponse());
        }

        SCAConsentResponseTO aisConsentResponse;
        try {
            aisConsentResponse = initiateConsentInternal(accountConsent, initialAspspConsentData);
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Initiate AIS consent failed: consent ID {}, devMessage {}", accountConsent.getId(), devMessage);
            return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT))
                           .build();
        }

        logger.info(SCA_STATUS_LOG, aisConsentResponse.getScaStatus());
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(aisConsentResponse));

        return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                       .payload(new SpiInitiateAisConsentResponse(accountConsent.getAccess(), false, ""))
                       .build();
    }

    /*
     * Maybe store the corresponding token in the list of revoked token.
     *
     * TODO: Implement this functionality
     *
     */
    @Override
    public SpiResponse<VoidResponse> revokeAisConsent(@NotNull SpiContextData contextData,
                                                      SpiAccountConsent accountConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class, false);
            sca.setScaStatus(ScaStatusTO.FINALISED);
            sca.setStatusDate(LocalDateTime.now());
            sca.setBearerToken(new BearerTokenTO());// remove existing token.

            String scaStatusName = sca.getScaStatus().name();
            logger.info(SCA_STATUS_LOG, scaStatusName);

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Revoke AIS consent failed: consent ID {}, devMessage {}", accountConsent.getId(), devMessage);
            return SpiResponse.<VoidResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, feignException.getMessage()))
                           .build();
        }

        return SpiResponse.<VoidResponse>builder()
                       .payload(SpiResponse.voidResponse())
                       .build();
    }

    /*
     * Verify tan, store resulting token in the returned accountConsent. The token
     * must be presented when TPP requests account information.
     *
     */
    @Override
    public @NotNull SpiResponse<SpiVerifyScaAuthorisationResponse> verifyScaAuthorisation(@NotNull SpiContextData contextData,
                                                                                          @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                          @NotNull SpiAccountConsent accountConsent,
                                                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAConsentResponseTO> authorizeConsentResponse = consentRestClient
                                                                                    .authorizeConsent(sca.getConsentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            SCAConsentResponseTO consentResponse = authorizeConsentResponse.getBody();

            String scaStatusName = sca.getScaStatus().name();
            logger.info(SCA_STATUS_LOG, scaStatusName);
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(consentResponse, !consentResponse.isPartiallyAuthorised()));

            // TODO use real sca status from Ledgers for resolving consent status https://git.adorsys.de/adorsys/xs2a/ledgers/issues/206
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .payload(new SpiVerifyScaAuthorisationResponse(getConsentStatus(consentResponse)))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Verify sca authorisation failed: consent ID {}, devMessage {}", accountConsent.getId(), devMessage);
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    ConsentStatus getConsentStatus(SCAConsentResponseTO consentResponse) {
        if (consentResponse != null
                    && consentResponse.isMultilevelScaRequired()
                    && consentResponse.isPartiallyAuthorised()
                    && ScaStatusTO.FINALISED.equals(consentResponse.getScaStatus())) {
            return ConsentStatus.PARTIALLY_AUTHORISED;
        }
        return ConsentStatus.VALID;
    }

    @Override
    protected String generatePsuMessage(@NotNull SpiContextData contextData, @NotNull String authorisationId,
                                        @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                        SpiResponse<SpiAuthorizationCodeResult> response) {
        List<String> challengeDataParts = Arrays.asList(response.getPayload().getChallengeData().getAdditionalInformation().split(" "));
        int indexOfTan = challengeDataParts.indexOf("is") + 1;
        String encryptedConsentId = "";
        try {
            encryptedConsentId = (String) FieldUtils.readField(aspspConsentDataProvider, "encryptedConsentId", true);
        } catch (IllegalAccessException e) {
            logger.error("could not read encrypted consent id");
        }
        String url = onlineBankingUrl.replace(USER_LOGIN, contextData.getPsuData().getPsuId())
                             .replace(CONSENT_ID, encryptedConsentId)
                             .replace(AUTH_ID, authorisationId)
                             .replace(TAN, challengeDataParts.get(indexOfTan));

        return format(DECOUPLED_USR_MSG, url);
    }

    private <T extends SpiInitiateAisConsentResponse> SpiResponse<T> firstCallInstantiatingConsent(
            @NotNull SpiAccountConsent accountConsent,
            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, T responsePayload) {
        SCAConsentResponseTO response = new SCAConsentResponseTO();
        response.setScaStatus(ScaStatusTO.STARTED);
        responsePayload.setAccountAccess(accountConsent.getAccess());
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response, false));
        return SpiResponse.<T>builder()
                       .payload(responsePayload)
                       .build();
    }

    @Override
    protected OpTypeTO getOtpType() {
        return OpTypeTO.CONSENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiAccountConsent businessObject) {
        logger.error("Initiate consent failed: consent ID {}", businessObject.getId());
        return new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT);
    }

    @Override
    protected ResponseEntity<SCAConsentResponseTO> getSelectMethodResponse(@NotNull String authenticationMethodId, SCAConsentResponseTO sca) {
        return consentRestClient.selectMethod(sca.getConsentId(), sca.getAuthorisationId(), authenticationMethodId);
    }

    @Override
    protected SCAConsentResponseTO getSCAConsentResponse(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        return consentDataService.response(aspspConsentData, SCAConsentResponseTO.class, checkCredentials);
    }

    @Override
    protected String getBusinessObjectId(SpiAccountConsent businessObject) {
        return businessObject.getId();
    }

    @Override
    protected SCAResponseTO initiateBusinessObject(SpiAccountConsent businessObject, byte[] aspspConsentData) {
        return initiateConsentInternal(businessObject, aspspConsentData);
    }

    @Override
    protected SCAConsentResponseTO mapToScaResponse(SpiAccountConsent businessObject, byte[] aspspConsentData, SCAConsentResponseTO originalResponse) throws IOException {
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(aspspConsentData, SCALoginResponseTO.class);
        SCAConsentResponseTO consentResponse = scaLoginMapper.toConsentResponse(scaResponseTO);
        consentResponse.setObjectType(SCAConsentResponseTO.class.getSimpleName());
        consentResponse.setConsentId(businessObject.getId());
        return consentResponse;
    }

    private SCAConsentResponseTO initiateConsentInternal(SpiAccountConsent accountConsent, byte[] initialAspspConsentData) throws FeignException {
        try {
            SCAResponseTO sca = consentDataService.response(initialAspspConsentData);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            AisConsentTO aisConsent = aisConsentMapper.mapToAisConsent(accountConsent);

            // Bearer token only returned in case of exempted consent.
            ResponseEntity<SCAConsentResponseTO> consentResponse = consentRestClient.startSCA(accountConsent.getId(),
                                                                                              aisConsent);
            SCAConsentResponseTO response = consentResponse.getBody();

            if (response != null && response.getBearerToken() == null) {
                response.setBearerToken(sca.getBearerToken());
            }
            return response;
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }
}