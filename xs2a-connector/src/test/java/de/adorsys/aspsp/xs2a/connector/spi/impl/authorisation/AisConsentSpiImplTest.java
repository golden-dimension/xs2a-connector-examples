package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.MultilevelScaService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.*;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.FINALISED;
import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.PSUAUTHENTICATED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AisConsentSpiImplTest {
    private static final String ACCESS_TOKEN = "access_token";
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String AUTHENTICATION_METHOD_ID = "authentication method id";

    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();

    private static final String CONFIRMATION_CODE = "code";

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";
    private static final String ONLINE_BANKING_URL_FIELD_NAME = "onlineBankingUrl";
    private static final String ONLINE_BANKING_URL_VALUE = "some.url";

    private JsonReader jsonReader = new JsonReader();
    private SpiAccountConsent spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent.json", SpiAccountConsent.class);
    private SpiAccountConsent spiAccountConsentAvailableAccounts = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-available-accounts.json", SpiAccountConsent.class);
    private SpiAccountConsent spiAccountConsentAvailableAccountsWithBalances = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-available-accounts-with-balances.json", SpiAccountConsent.class);
    private SpiAccountConsent spiAccountConsentGlobal = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-global.json", SpiAccountConsent.class);

    @InjectMocks
    private AisConsentSpiImpl spi;

    @Mock
    private ConsentRestClient consentRestClient;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Spy
    private AisConsentMapper aisConsentMapper = Mappers.getMapper(AisConsentMapper.class);
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private GeneralAuthorisationService authorisationService;
    @Mock
    private GlobalScaResponseTO scaResponseTO;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private ScaMethodConverter scaMethodConverter;
    @Mock
    private AccountRestClient accountRestClient;
    @Mock
    private LedgersSpiAccountMapper accountMapper;
    @Mock
    private MultilevelScaService multilevelScaService;
    @Mock
    private KeycloakTokenService keycloakTokenService;
    @Mock
    private UserMgmtRestClient userMgmtRestClient;

    @Test
    void initiateAisConsent_WithInitialAspspConsentData() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void initiateAisConsent_WithInitialAspspConsentData_availableAccounts() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentAvailableAccounts);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentAvailableAccounts.getAccess());
        spiAccountConsent.setAccess(spiAccountAccess);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(4)).getAccess();
        verify(spiAccountAccess).setAccounts(spiAccountReferences);
    }

    @Test
    void initiateAisConsent_WithInitialAspspConsentData_availableAccountsWithBalances() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentAvailableAccountsWithBalances);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentAvailableAccountsWithBalances.getAccess());
        spiAccountConsent.setAccess(spiAccountAccess);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(4)).getAccess();
        verify(spiAccountAccess).setAccounts(spiAccountReferences);
    }

    @Test
    void initiateAisConsent_WithInitialAspspConsentData_global() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentGlobal);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentGlobal.getAccess());
        spiAccountConsent.setAccess(spiAccountAccess);

        when(spiAccountConsent.getId()).thenReturn(CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(4)).getAccess();
        verify(spiAccountAccess).setAccounts(spiAccountReferences);
        verify(spiAccountAccess).setTransactions(spiAccountReferences);
        verify(spiAccountAccess).setBalances(spiAccountReferences);
    }


    @Test
    void initiateAisConsent_WithEmptyInitialAspspConsentData() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiAccountConsent.getAccess().getAccounts().get(0)))).thenReturn(true);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(spiAspspConsentDataProvider).updateAspspConsentData(any());
    }

    @Test
    void initiateAisConsent_WithEmptyInitialAspspConsentDataAndFeignException() {
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiAccountConsent.getAccess().getAccounts().get(0)))).thenThrow(getFeignException());

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        assertTrue(actualResponse.getErrors().contains(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT)));
    }

    @Test
    void initiateAisConsent_WithException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenThrow(FeignException.errorStatus(RESPONSE_STATUS_200_WITH_EMPTY_BODY,
                                                      buildErrorResponse()));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
    }

    @Test
    void authorisePsu() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        String password = "password";

        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        when(keycloakTokenService.login(spiPsuData.getPsuId(), password)).thenReturn(token);

        spiAccountConsent.setId(CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));


        when(authorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS)).build());

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actualResponse.hasError());
        assertEquals(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS), actualResponse.getPayload());

        verify(authRequestInterceptor, times(3)).setAccessToken(scaConsentResponseTO.getBearerToken().getAccess_token());
        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentRestClient).initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class));
        verify(redirectScaRestClient).startSca(any(StartScaOprTO.class));
        verify(authorisationService).authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void authorisePsu_consentInternalError() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        String password = "password";

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        sca.setBearerToken(token);
        token.setAccess_token(ACCESS_TOKEN);
        when(keycloakTokenService.login(spiPsuData.getPsuId(), password)).thenReturn(token);

        spiAccountConsent.setId(CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.badRequest().build());

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actual = spi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.isSuccessful());
        assertEquals(SpiAuthorisationStatus.FAILURE, actual.getPayload().getSpiAuthorisationStatus());

        verify(authRequestInterceptor, times(3)).setAccessToken(scaConsentResponseTO.getBearerToken().getAccess_token());
        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentRestClient).initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class));
        verify(redirectScaRestClient).startSca(any(StartScaOprTO.class));
        verify(authorisationService, never()).authorisePsuInternal(anyString(), anyString(), any(), any(), any());
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void authorisePsu_feignExceptionOnGetSCAConsentResponse() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        String password = "password";
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        sca.setBearerToken(token);
        token.setAccess_token(ACCESS_TOKEN);
        when(keycloakTokenService.login(spiPsuData.getPsuId(), password)).thenReturn(token);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        spiAccountConsent.setId(CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class)))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actualResponse.getErrors().get(0).getErrorCode());
    }

    @Test
    void revokeAisConsent() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        when(consentDataService.response(CONSENT_DATA_BYTES, false)).thenReturn(initialConsentResponseTO);

        ArgumentCaptor<GlobalScaResponseTO> consentResponseCaptor = ArgumentCaptor.forClass(GlobalScaResponseTO.class);

        // When
        SpiResponse<SpiResponse.VoidResponse> actual = spi.revokeAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        verify(consentDataService).store(consentResponseCaptor.capture());

        assertFalse(actual.hasError());
    }

    @Test
    void revokeAisConsent_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(consentDataService.response(CONSENT_DATA_BYTES, false))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));

        // When
        SpiResponse<SpiResponse.VoidResponse> actual = spi.revokeAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenReturn(initialConsentResponseTO);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        GlobalScaResponseTO authoriseConsentResponseTO = new GlobalScaResponseTO();
        authoriseConsentResponseTO.setScaStatus(PSUAUTHENTICATED);
        when(redirectScaRestClient.validateScaCode("authorisation id", "tan")).thenReturn(ResponseEntity.ok(authoriseConsentResponseTO));

        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(ConsentStatus.VALID);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void verifyScaAuthorisation_partiallyAuthorised() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenReturn(initialConsentResponseTO);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        GlobalScaResponseTO authoriseConsentResponseTO = new GlobalScaResponseTO();
        authoriseConsentResponseTO.setMultilevelScaRequired(true);
        authoriseConsentResponseTO.setPartiallyAuthorised(true);
        authoriseConsentResponseTO.setScaStatus(FINALISED);
        when(redirectScaRestClient.validateScaCode("authorisation id", "tan")).thenReturn(ResponseEntity.ok(authoriseConsentResponseTO));

        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(ConsentStatus.PARTIALLY_AUTHORISED);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void verifyScaAuthorisation_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message");
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenThrow(feignException);
        when(feignExceptionReader.getErrorCode(feignException)).thenReturn("error code");

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation_feignExceptionAttemptFailure() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message");
        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorCode(feignException)).thenReturn("SCA_VALIDATION_ATTEMPT_FAILED");

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");
        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(null, SpiAuthorisationStatus.ATTEMPT_FAILURE);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertNotNull(actual.getPayload());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void requestAvailableScaMethods_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        when(redirectScaRestClient.getSCA(AUTHORISATION_ID)).thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAvailableScaMethods_scaMethodUnknown() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(null);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        when(redirectScaRestClient.getSCA(AUTHORISATION_ID)).thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(Collections.emptyList(), actual.getPayload().getAvailableScaMethods());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAvailableScaMethods_feignException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaConsentResponseTO);

        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_SCA_METHODS, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAuthorisationCode() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id"))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(expected)
                                    .build());

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void requestAuthorisationCode_feignException501() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.NOT_IMPLEMENTED, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SCA_METHOD_UNKNOWN, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAuthorisationCode_feignException400() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAuthorisationCode_feignException404() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAuthorisationCode_noBearerTokenInSelectMethodResponse() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        GlobalScaResponseTO selectMethodScaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED, null);
        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(selectMethodScaConsentResponseTO));

        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id"))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(expected)
                                    .build());

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void startScaDecoupled_success() {
        SpiAspspConsentDataProvider spiAspspConsentDataProviderWithEncryptedId = new SpiAspspConsentDataProviderWithEncryptedId(spiAspspConsentDataProvider, CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        SpiAuthorizationCodeResult payload = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        payload.setChallengeData(challengeData);
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id"))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(payload)
                                    .build());

        ReflectionTestUtils.setField(spi, "onlineBankingUrl", "some.url");

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, "authorisation id", "authentication method id",
                                                                                         spiAccountConsent, spiAspspConsentDataProviderWithEncryptedId);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient).selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService).returnScaMethodSelection(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id");
    }

    @Test
    void startScaDecoupled_errorOnReturningScaMethodSelection() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id"))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");


        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                         spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals("", actual.getErrors().get(0).getMessageText());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient).selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService).returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id");
        verify(feignExceptionReader).getErrorMessage(any(FeignException.class));
    }

    @Test
    void startScaDecoupled_scaSelected() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult payload = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        payload.setChallengeData(challengeData);

        SpiAspspConsentDataProvider spiAspspConsentDataProviderWithEncryptedId = new SpiAspspConsentDataProviderWithEncryptedId(spiAspspConsentDataProvider, CONSENT_ID);

        when(authorisationService.getResponseIfScaSelected(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id"))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(payload)
                                    .build());

        ReflectionTestUtils.setField(spi, ONLINE_BANKING_URL_FIELD_NAME, ONLINE_BANKING_URL_VALUE);

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                         spiAccountConsent, spiAspspConsentDataProviderWithEncryptedId);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(authorisationService).getResponseIfScaSelected(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id");
    }

    @Test
    void requestAvailableScaMethods_authenticationMethodIdIsNull() {
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, null,
                                                                                         spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void checkConfirmationCode_success() {
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        scaConsentResponseTO.setBearerToken(bearerTokenTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaConsentResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO();
        authConfirmationTO.setSuccess(true);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual = spi.checkConfirmationCode(SPI_CONTEXT_DATA,
                                                                                                     new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID),
                                                                                                     spiAspspConsentDataProvider);
        assertFalse(actual.hasError());
        assertEquals(ScaStatus.FINALISED, actual.getPayload().getScaStatus());
        assertEquals(ConsentStatus.VALID, actual.getPayload().getConsentStatus());

        verify(userMgmtRestClient).verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void checkConfirmationCode_notSuccess() {
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        scaConsentResponseTO.setBearerToken(bearerTokenTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaConsentResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO();
        authConfirmationTO.setSuccess(false);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual = spi.checkConfirmationCode(SPI_CONTEXT_DATA,
                                                                                                     new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID),
                                                                                                     spiAspspConsentDataProvider);
        assertFalse(actual.hasError());
        assertEquals(ScaStatus.FAILED, actual.getPayload().getScaStatus());
        assertEquals(ConsentStatus.REJECTED, actual.getPayload().getConsentStatus());

        verify(userMgmtRestClient).verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void checkConfirmationCode_partiallyAuthorised() {
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        scaConsentResponseTO.setBearerToken(bearerTokenTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaConsentResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO();
        authConfirmationTO.setSuccess(true);
        authConfirmationTO.setPartiallyAuthorised(true);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual = spi.checkConfirmationCode(SPI_CONTEXT_DATA,
                                                                                                     new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID),
                                                                                                     spiAspspConsentDataProvider);
        assertFalse(actual.hasError());
        assertEquals(ScaStatus.FINALISED, actual.getPayload().getScaStatus());
        assertEquals(ConsentStatus.PARTIALLY_AUTHORISED, actual.getPayload().getConsentStatus());

        verify(userMgmtRestClient).verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void checkConfirmationCode_feignError() {
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        scaConsentResponseTO.setBearerToken(bearerTokenTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaConsentResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO();
        authConfirmationTO.setSuccess(true);
        authConfirmationTO.setPartiallyAuthorised(true);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenThrow(getFeignException());

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual = spi.checkConfirmationCode(SPI_CONTEXT_DATA,
                                                                                                     new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID),
                                                                                                     spiAspspConsentDataProvider);
        assertFalse(actual.getErrors().isEmpty());
        assertNull(actual.getPayload());
        assertTrue(actual.getErrors().contains(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID)));

        verify(userMgmtRestClient).verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    private Response buildErrorResponse() {
        return Response.builder()
                       .status(404)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

    private GlobalScaResponseTO buildSCAConsentResponseTO(ScaStatusTO scaStatusTO) {
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        return buildSCAConsentResponseTO(scaStatusTO, bearerToken);
    }

    private GlobalScaResponseTO buildSCAConsentResponseTO(ScaStatusTO scaStatusTO, BearerTokenTO bearerTokenTO) {
        GlobalScaResponseTO scaConsentResponseTO = new GlobalScaResponseTO();
        scaConsentResponseTO.setOperationObjectId(CONSENT_ID);
        scaConsentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaConsentResponseTO.setScaStatus(scaStatusTO);
        scaConsentResponseTO.setBearerToken(bearerTokenTO);
        return scaConsentResponseTO;
    }

    private List<AccountDetailsTO> buildListOfAccounts() {
        return Collections.singletonList(jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class));
    }

    private List<SpiAccountDetails> buildSpiAccountDetails() {
        return Collections.singletonList(jsonReader.getObjectFromFile("json/spi/impl/spi-account-details.json", SpiAccountDetails.class));
    }

    private FeignException getFeignException() {
        return FeignException.errorStatus("User doesn't have access to the requested account",
                                          buildErrorResponseForbidden());
    }

    private Response buildErrorResponseForbidden() {
        return Response.builder()
                       .status(403)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }
}