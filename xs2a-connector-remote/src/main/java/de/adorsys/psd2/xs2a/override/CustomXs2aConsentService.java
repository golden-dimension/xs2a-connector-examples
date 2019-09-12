package de.adorsys.psd2.xs2a.override;

import de.adorsys.psd2.event.core.model.EventType;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.domain.ErrorHolder;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.consent.CreateConsentReq;
import de.adorsys.psd2.xs2a.domain.consent.CreateConsentResponse;
import de.adorsys.psd2.xs2a.domain.consent.Xs2aAccountAccess;
import de.adorsys.psd2.xs2a.exception.MessageError;
import de.adorsys.psd2.xs2a.service.ConsentService;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.service.TppService;
import de.adorsys.psd2.xs2a.service.authorization.AuthorisationMethodDecider;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisScaAuthorisationService;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisScaAuthorisationServiceResolver;
import de.adorsys.psd2.xs2a.service.consent.AccountReferenceInConsentUpdater;
import de.adorsys.psd2.xs2a.service.consent.Xs2aAisConsentService;
import de.adorsys.psd2.xs2a.service.context.SpiContextDataProvider;
import de.adorsys.psd2.xs2a.service.event.Xs2aEventService;
import de.adorsys.psd2.xs2a.service.mapper.consent.Xs2aAisConsentMapper;
import de.adorsys.psd2.xs2a.service.mapper.psd2.ServiceType;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.SpiErrorMapper;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.SpiToXs2aAccountAccessMapper;
import de.adorsys.psd2.xs2a.service.spi.InitialSpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.service.spi.SpiAspspConsentDataProviderFactory;
import de.adorsys.psd2.xs2a.service.validator.AisEndpointAccessCheckerService;
import de.adorsys.psd2.xs2a.service.validator.ValidationResult;
import de.adorsys.psd2.xs2a.service.validator.ais.consent.*;
import de.adorsys.psd2.xs2a.service.validator.ais.consent.dto.CreateConsentRequestObject;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.RESOURCE_UNKNOWN_400;
import static de.adorsys.psd2.xs2a.domain.TppMessageInformation.of;
import static de.adorsys.psd2.xs2a.service.mapper.psd2.ErrorType.AIS_400;

@Slf4j
@Primary
@Service
public class CustomXs2aConsentService extends ConsentService {
    private final Xs2aAisConsentMapper aisConsentMapper;
    private final SpiToXs2aAccountAccessMapper spiToXs2aAccountAccessMapper;
    private final Xs2aAisConsentService aisConsentService;
    private final TppService tppService;
    private final SpiContextDataProvider spiContextDataProvider;
    private final AuthorisationMethodDecider authorisationMethodDecider;
    private final AisConsentSpi aisConsentSpi;
    private final Xs2aEventService xs2aEventService;
    private final AccountReferenceInConsentUpdater accountReferenceUpdater;
    private final SpiErrorMapper spiErrorMapper;

    private final CreateConsentRequestValidator createConsentRequestValidator;

    private final AisScaAuthorisationService aisScaAuthorisationService;
    private final RequestProviderService requestProviderService;
    private final SpiAspspConsentDataProviderFactory aspspConsentDataProviderFactory;
    private final AisScaAuthorisationServiceResolver aisScaAuthorisationServiceResolver;

    private final CustomXs2aAisConsentService customXs2aAisConsentService;

    public CustomXs2aConsentService(Xs2aAisConsentMapper aisConsentMapper, SpiToXs2aAccountAccessMapper spiToXs2aAccountAccessMapper, Xs2aAisConsentService aisConsentService, AisScaAuthorisationServiceResolver aisScaAuthorisationServiceResolver, TppService tppService, AisEndpointAccessCheckerService endpointAccessCheckerService, SpiContextDataProvider spiContextDataProvider, AuthorisationMethodDecider authorisationMethodDecider, AisConsentSpi aisConsentSpi, Xs2aEventService xs2aEventService, AccountReferenceInConsentUpdater accountReferenceUpdater, SpiErrorMapper spiErrorMapper, CreateConsentRequestValidator createConsentRequestValidator, GetAccountConsentsStatusByIdValidator getAccountConsentsStatusByIdValidator, GetAccountConsentByIdValidator getAccountConsentByIdValidator, DeleteAccountConsentsByIdValidator deleteAccountConsentsByIdValidator, CreateConsentAuthorisationValidator createConsentAuthorisationValidator, UpdateConsentPsuDataValidator updateConsentPsuDataValidator, GetConsentAuthorisationsValidator getConsentAuthorisationsValidator, GetConsentAuthorisationScaStatusValidator getConsentAuthorisationScaStatusValidator, AisScaAuthorisationService aisScaAuthorisationService, RequestProviderService requestProviderService, SpiAspspConsentDataProviderFactory aspspConsentDataProviderFactory,
                                    CustomXs2aAisConsentService customXs2aAisConsentService) {
        super(aisConsentMapper, spiToXs2aAccountAccessMapper, aisConsentService, aisScaAuthorisationServiceResolver, tppService, endpointAccessCheckerService, spiContextDataProvider, authorisationMethodDecider, aisConsentSpi, xs2aEventService, accountReferenceUpdater, spiErrorMapper, createConsentRequestValidator, getAccountConsentsStatusByIdValidator, getAccountConsentByIdValidator, deleteAccountConsentsByIdValidator, createConsentAuthorisationValidator, updateConsentPsuDataValidator, getConsentAuthorisationsValidator, getConsentAuthorisationScaStatusValidator, aisScaAuthorisationService, requestProviderService, aspspConsentDataProviderFactory);
        this.aisConsentMapper = aisConsentMapper;
        this.spiToXs2aAccountAccessMapper = spiToXs2aAccountAccessMapper;
        this.aisConsentService = aisConsentService;
        this.tppService = tppService;
        this.spiContextDataProvider = spiContextDataProvider;
        this.authorisationMethodDecider = authorisationMethodDecider;
        this.aisConsentSpi = aisConsentSpi;
        this.xs2aEventService = xs2aEventService;
        this.accountReferenceUpdater = accountReferenceUpdater;
        this.spiErrorMapper = spiErrorMapper;
        this.createConsentRequestValidator = createConsentRequestValidator;
        this.aisScaAuthorisationService = aisScaAuthorisationService;
        this.requestProviderService = requestProviderService;
        this.aspspConsentDataProviderFactory = aspspConsentDataProviderFactory;
        this.aisScaAuthorisationServiceResolver = aisScaAuthorisationServiceResolver;
        this.customXs2aAisConsentService = customXs2aAisConsentService;
    }

    @Override
    public ResponseObject<CreateConsentResponse> createAccountConsentsWithResponse(CreateConsentReq request, PsuIdData psuData, boolean explicitPreferred) {
        xs2aEventService.recordTppRequest(EventType.CREATE_AIS_CONSENT_REQUEST_RECEIVED, request);

        ValidationResult validationResult = createConsentRequestValidator.validate(new CreateConsentRequestObject(request, psuData));
        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}]. Create account consent with response - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), validationResult.getMessageError());
            return ResponseObject.<CreateConsentResponse>builder()
                           .fail(validationResult.getMessageError())
                           .build();
        }

        if (request.isGlobalOrAllAccountsAccessConsent()) {
            request.setAccess(getAccessForGlobalOrAllAvailableAccountsConsent(request));
        }

        TppInfo tppInfo = tppService.getTppInfo();
        CustomXs2aCreateAisConsentResponse response = customXs2aAisConsentService.customCreateConsent(request, psuData, tppInfo);

        if (StringUtils.isBlank(response.getConsentId())) {
            return ResponseObject.<CreateConsentResponse>builder()
                           .fail(AIS_400, of(RESOURCE_UNKNOWN_400))
                           .build();
        }

        SpiContextData contextData = spiContextDataProvider.provide(psuData, tppInfo);
        InitialSpiAspspConsentDataProvider aspspConsentDataProvider = aspspConsentDataProviderFactory.getInitialAspspConsentDataProvider();

        String consentId = response.getConsentId();
        SpiResponse<SpiInitiateAisConsentResponse> initiateAisConsentSpiResponse = aisConsentSpi.initiateAisConsent(contextData, aisConsentMapper.mapToSpiAccountConsent(response.getAccountConsent()), aspspConsentDataProvider);
        aspspConsentDataProvider.saveWith(consentId);

        if (initiateAisConsentSpiResponse.hasError()) {
            aisConsentService.updateConsentStatus(consentId, ConsentStatus.REJECTED);
            ErrorHolder errorHolder = spiErrorMapper.mapToErrorHolder(initiateAisConsentSpiResponse, ServiceType.AIS);
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create account consent  with response failed. Consent rejected. Couldn't initiate AIS consent at SPI level: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, errorHolder);
            return ResponseObject.<CreateConsentResponse>builder()
                           .fail(new MessageError(errorHolder))
                           .build();
        }

        SpiInitiateAisConsentResponse spiResponsePayload = initiateAisConsentSpiResponse.getPayload();
        boolean multilevelScaRequired = spiResponsePayload.isMultilevelScaRequired()
                                                && !aisScaAuthorisationService.isOneFactorAuthorisation(request.isConsentForAllAvailableAccounts(), request.isOneAccessType());

        updateMultilevelSca(consentId, multilevelScaRequired);

        Optional<Xs2aAccountAccess> xs2aAccountAccess = spiToXs2aAccountAccessMapper.mapToAccountAccess(spiResponsePayload.getAccountAccess());
        xs2aAccountAccess.ifPresent(accountAccess ->
                                            accountReferenceUpdater.rewriteAccountAccess(consentId, accountAccess));

        CreateConsentResponse createConsentResponse = new CreateConsentResponse(ConsentStatus.RECEIVED.getValue(), consentId, null, null, null, spiResponsePayload.getPsuMessage(), multilevelScaRequired);
        ResponseObject<CreateConsentResponse> createConsentResponseObject = ResponseObject.<CreateConsentResponse>builder().body(createConsentResponse).build();

        if (authorisationMethodDecider.isImplicitMethod(explicitPreferred, multilevelScaRequired)) {
            proceedImplicitCaseForCreateConsent(createConsentResponseObject.getBody(), psuData, consentId);
        }

        return createConsentResponseObject;
    }

    private Xs2aAccountAccess getAccessForGlobalOrAllAvailableAccountsConsent(CreateConsentReq request) {
        return new Xs2aAccountAccess(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                request.getAccess().getAvailableAccounts(),
                request.getAccess().getAllPsd2(),
                request.getAccess().getAvailableAccountsWithBalance()
        );
    }

    private void updateMultilevelSca(String consentId, boolean multilevelScaRequired) {
        // default value is false, so we do the call only for non-default (true) case
        if (multilevelScaRequired) {
            aisConsentService.updateMultilevelScaRequired(consentId, multilevelScaRequired);
        }
    }

    private void proceedImplicitCaseForCreateConsent(CreateConsentResponse response, PsuIdData psuData, String consentId) {
        aisScaAuthorisationServiceResolver.getService().createConsentAuthorization(psuData, consentId)
                .ifPresent(a -> response.setAuthorizationId(a.getAuthorisationId()));
    }

}
