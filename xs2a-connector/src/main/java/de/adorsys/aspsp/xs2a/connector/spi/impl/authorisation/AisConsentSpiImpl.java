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

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.MultilevelScaService;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.consent.*;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse.VoidResponse;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import feign.FeignException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

@Component
public class AisConsentSpiImpl extends AbstractAuthorisationSpi<SpiAccountConsent> implements AisConsentSpi {
    private static final String ATTEMPT_FAILURE = "SCA_VALIDATION_ATTEMPT_FAILED";
    private static final Logger logger = LoggerFactory.getLogger(AisConsentSpiImpl.class);
    private static final String USER_LOGIN = "{userLogin}";
    private static final String CONSENT_ID = "{consentId}";
    private static final String AUTH_ID = "{authorizationId}";
    private static final String TAN = "{tan}";
    private static final String DECOUPLED_USR_MSG = "Please check your app to continue... %s";

    private static final String SCA_STATUS_LOG = "SCA status is {}";

    private final AccountRestClient accountRestClient;
    private final LedgersSpiAccountMapper accountMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final MultilevelScaService multilevelScaService;
    private final UserMgmtRestClient userMgmtRestClient;
    private final RedirectScaRestClient redirectScaRestClient;

    @Value("${xs2asandbox.tppui.online-banking.url}")
    private String onlineBankingUrl;

    public AisConsentSpiImpl(AuthRequestInterceptor authRequestInterceptor,
                             AspspConsentDataService consentDataService, GeneralAuthorisationService authorisationService,
                             ScaMethodConverter scaMethodConverter, FeignExceptionReader feignExceptionReader,
                             AccountRestClient accountRestClient, LedgersSpiAccountMapper accountMapper, MultilevelScaService multilevelScaService, UserMgmtRestClient userMgmtRestClient, RedirectScaRestClient redirectScaRestClient,
                             KeycloakTokenService keycloakTokenService) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader, keycloakTokenService, redirectScaRestClient);
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
        this.accountRestClient = accountRestClient;
        this.accountMapper = accountMapper;
        this.multilevelScaService = multilevelScaService;
        this.userMgmtRestClient = userMgmtRestClient;
        this.redirectScaRestClient = redirectScaRestClient;
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
            return firstCallInstantiatingConsent(accountConsent, aspspConsentDataProvider, new SpiInitiateAisConsentResponse(), contextData.getPsuData());
        }

        GlobalScaResponseTO globalScaResponse;
        try {
            globalScaResponse = initiateConsentInternal(accountConsent, initialAspspConsentData);
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Initiate AIS consent failed: consent ID: {}, devMessage: {}", accountConsent.getId(), devMessage);
            return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT))
                           .build();
        }

        logger.info(SCA_STATUS_LOG, globalScaResponse.getScaStatus());
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(globalScaResponse));

        return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                       .payload(new SpiInitiateAisConsentResponse(accountConsent.getAccess(), false, ""))
                       .build();
    }

    @Override
    public SpiResponse<SpiConsentStatusResponse> getConsentStatus(@NotNull SpiContextData contextData, @NotNull SpiAccountConsent accountConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return SpiResponse.<SpiConsentStatusResponse>builder()
                       .payload(new SpiConsentStatusResponse(accountConsent.getConsentStatus(), "Mocked PSU message from SPI for this consent"))
                       .build();
    }

    @Override
    public SpiResponse<VoidResponse> revokeAisConsent(@NotNull SpiContextData contextData,
                                                      SpiAccountConsent accountConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), false);
            sca.setScaStatus(ScaStatusTO.FINALISED);
            sca.setStatusDate(LocalDateTime.now());
            sca.setBearerToken(new BearerTokenTO());// remove existing token.

            String scaStatusName = sca.getScaStatus().name();
            logger.info(SCA_STATUS_LOG, scaStatusName);

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Revoke AIS consent failed: consent ID: {}, devMessage: {}", accountConsent.getId(), devMessage);
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
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<GlobalScaResponseTO> authorizeConsentResponse = redirectScaRestClient.validateScaCode(sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            GlobalScaResponseTO authorizeConsentResponseBody = authorizeConsentResponse.getBody();

            String scaStatusName = sca.getScaStatus().name();
            logger.info(SCA_STATUS_LOG, scaStatusName);
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(authorizeConsentResponseBody, !authorizeConsentResponseBody.isPartiallyAuthorised()));

            // TODO use real sca status from Ledgers for resolving consent status https://git.adorsys.de/adorsys/xs2a/ledgers/issues/206
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .payload(new SpiVerifyScaAuthorisationResponse(getConsentStatus(authorizeConsentResponseBody)))
                           .build();

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Verify sca authorisation failed: consent ID {}, devMessage {}", accountConsent.getId(), devMessage);

            String errorCode = feignExceptionReader.getErrorCode(feignException);
            if (errorCode.equals(ATTEMPT_FAILURE)) {
                return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                               .payload(new SpiVerifyScaAuthorisationResponse(accountConsent.getConsentStatus(), SpiAuthorisationStatus.ATTEMPT_FAILURE))
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                               .build();
            }

            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiConsentConfirmationCodeValidationResponse> checkConfirmationCode(@NotNull SpiContextData spiContextData, @NotNull SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {

        try {
            GlobalScaResponseTO sca = consentDataService.response(spiAspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<AuthConfirmationTO> authConfirmationTOResponse =
                    userMgmtRestClient.verifyAuthConfirmationCode(spiCheckConfirmationCodeRequest.getAuthorisationId(), spiCheckConfirmationCodeRequest.getConfirmationCode());

            AuthConfirmationTO authConfirmationTO = authConfirmationTOResponse.getBody();

            if (authConfirmationTO == null || !authConfirmationTO.isSuccess()) {
                // No response in payload from ASPSP or confirmation code verification failed at ASPSP side.
                return getConfirmationCodeResponseForXs2a(ScaStatus.FAILED, ConsentStatus.REJECTED);
            }

            if (authConfirmationTO.isPartiallyAuthorised()) {
                // This authorisation is finished, but others are left.
                return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, ConsentStatus.PARTIALLY_AUTHORISED);
            }

            // Authorisation is finalised and consent becomes valid.
            return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, ConsentStatus.VALID);

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiConsentConfirmationCodeValidationResponse> notifyConfirmationCodeValidation(@NotNull SpiContextData spiContextData, @NotNull boolean confirmationCodeValidationResult, @NotNull SpiAccountConsent spiAccountConsent, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        ScaStatus scaStatus = confirmationCodeValidationResult ? ScaStatus.FINALISED : ScaStatus.FAILED;
        ConsentStatus consentStatus = confirmationCodeValidationResult ? ConsentStatus.VALID : ConsentStatus.REJECTED;

        SpiConsentConfirmationCodeValidationResponse response = new SpiConsentConfirmationCodeValidationResponse(scaStatus, consentStatus);

        return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
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

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiAccountConsent businessObject, GlobalScaResponseTO scaConsentResponseTO) {
        return !scaConsentResponseTO.isMultilevelScaRequired() || businessObject.getPsuData().size() <= 1;
    }

    @Override
    protected OpTypeTO getOpType() {
        return OpTypeTO.CONSENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiAccountConsent businessObject) {
        logger.error("Initiate consent failed: consent ID: {}", businessObject.getId());
        return new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT);
    }

    @Override
    protected String getBusinessObjectId(SpiAccountConsent businessObject) {
        return businessObject.getId();
    }

    @Override
    protected GlobalScaResponseTO initiateBusinessObject(SpiAccountConsent businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return initiateConsentInternal(businessObject, aspspConsentDataProvider.loadAspspConsentData());
    }

    @Override
    protected SpiResponse<SpiAvailableScaMethodsResponse> getForZeroScaMethods(ScaStatusTO status) {
        return SpiResponse.<SpiAvailableScaMethodsResponse>builder()
                       .payload(new SpiAvailableScaMethodsResponse(Collections.emptyList()))
                       .build();
    }

    @Override
    public @NotNull SpiResponse<Boolean> requestTrustedBeneficiaryFlag(@NotNull SpiContextData spiContextData, @NotNull SpiAccountConsent accountConsent, @NotNull String authorisationId, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        // TODO replace with real response from ledgers https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1263
        logger.info("Retrieving mock trusted beneficiaries flag for consent: {}", accountConsent);
        return SpiResponse.<Boolean>builder()
                       .payload(true)
                       .build();
    }

    private GlobalScaResponseTO initiateConsentInternal(SpiAccountConsent accountConsent, byte[] initialAspspConsentData) {
        try {
            GlobalScaResponseTO sca = consentDataService.response(initialAspspConsentData);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            SpiAccountAccess spiAccountAccess = accountConsent.getAccess();
            boolean isAllAvailableAccounts = spiAccountAccess.getAvailableAccounts() != null;
            boolean isAllAvailableAccountsWithBalance = spiAccountAccess.getAvailableAccountsWithBalance() != null;
            boolean isAllPsd2 = spiAccountAccess.getAllPsd2() != null;

            if (isAllAvailableAccounts || isAllAvailableAccountsWithBalance || isAllPsd2) {
                List<SpiAccountReference> references = getReferences();
                spiAccountAccess.setAccounts(references);

                if (isAllAvailableAccountsWithBalance || isAllPsd2) {
                    spiAccountAccess.setBalances(references);
                }

                if (isAllPsd2) {
                    spiAccountAccess.setTransactions(references);
                }
            }

            StartScaOprTO startScaOprTO = new StartScaOprTO();
            startScaOprTO.setOpType(OpTypeTO.CONSENT);
            startScaOprTO.setAuthorisationId(sca.getAuthorisationId());
            startScaOprTO.setOprId(accountConsent.getId());

            // Bearer token only returned in case of exempted consent.
            ResponseEntity<GlobalScaResponseTO> consentResponse = redirectScaRestClient.startSca(startScaOprTO);
            return consentResponse.getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private List<SpiAccountReference> getReferences() {
        return Optional.ofNullable(accountRestClient.getListOfAccounts().getBody())
                       .map(l -> l.stream().map(accountMapper::toSpiAccountDetails)
                                         .map(SpiAccountReference::new).collect(Collectors.toList()))
                       .orElseGet(Collections::emptyList);
    }

    private boolean isCardAccountConsent(Set<SpiAccountReference> spiAccountReferences) {
        return spiAccountReferences.stream()
                       .anyMatch(ref -> StringUtils.isNotBlank(ref.getMaskedPan())
                                                || StringUtils.isNotBlank(ref.getPan()));
    }

    private ConsentStatus getConsentStatus(GlobalScaResponseTO globalScaResponse) {
        if (globalScaResponse != null
                    && globalScaResponse.isPartiallyAuthorised()
                    && ScaStatusTO.FINALISED.equals(globalScaResponse.getScaStatus())) {
            return ConsentStatus.PARTIALLY_AUTHORISED;
        }
        return ConsentStatus.VALID;
    }

    private boolean isMultilevelScaRequired(@NotNull SpiAccountConsent accountConsent, @NotNull SpiPsuData spiPsuData) {
        SpiAccountAccess access = accountConsent.getAccess();

        Set<SpiAccountReference> spiAccountReferences = Stream.of(access.getAccounts(), access.getBalances(), access.getTransactions())
                                                                .flatMap(Collection::stream)
                                                                .collect(Collectors.toSet());

        if (isCardAccountConsent(spiAccountReferences)) { // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
            return false;
        }

        return multilevelScaService.isMultilevelScaRequired(spiPsuData, spiAccountReferences);
    }

    private SpiResponse<SpiConsentConfirmationCodeValidationResponse> getConfirmationCodeResponseForXs2a(ScaStatus scaStatus, ConsentStatus consentStatus) {
        SpiConsentConfirmationCodeValidationResponse response = new SpiConsentConfirmationCodeValidationResponse(scaStatus, consentStatus);

        return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
    }

    private <T extends SpiInitiateAisConsentResponse> SpiResponse<T> firstCallInstantiatingConsent(
            @NotNull SpiAccountConsent accountConsent,
            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, T responsePayload,
            @NotNull SpiPsuData spiPsuData) {
        boolean isMultilevelScaRequired;

        try {
            isMultilevelScaRequired = isMultilevelScaRequired(accountConsent, spiPsuData);
        } catch (FeignException e) {
            logger.error("Error during REST call for consent initiation to ledgers for account multilevel checking, PSU ID: {}", spiPsuData.getPsuId());
            return SpiResponse.<T>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT))
                           .build();
        }

        GlobalScaResponseTO response = new GlobalScaResponseTO();
        response.setOpType(OpTypeTO.CONSENT);
        response.setScaStatus(ScaStatusTO.STARTED);
        response.setMultilevelScaRequired(isMultilevelScaRequired);
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response, false));

        responsePayload.setAccountAccess(accountConsent.getAccess());
        responsePayload.setMultilevelScaRequired(isMultilevelScaRequired);

        return SpiResponse.<T>builder()
                       .payload(responsePayload)
                       .build();
    }
}
