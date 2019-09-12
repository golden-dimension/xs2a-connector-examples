package de.adorsys.psd2.xs2a.override;

import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.TppMessageInformation;
import de.adorsys.psd2.xs2a.domain.consent.CreateConsentReq;
import de.adorsys.psd2.xs2a.domain.consent.CreateConsentResponse;
import de.adorsys.psd2.xs2a.exception.MessageError;
import de.adorsys.psd2.xs2a.service.RedirectIdService;
import de.adorsys.psd2.xs2a.service.ScaApproachResolver;
import de.adorsys.psd2.xs2a.service.authorization.AuthorisationMethodDecider;
import de.adorsys.psd2.xs2a.service.message.MessageService;
import de.adorsys.psd2.xs2a.service.profile.AspspProfileServiceWrapper;
import de.adorsys.psd2.xs2a.web.RedirectLinkBuilder;
import de.adorsys.psd2.xs2a.web.aspect.AbstractLinkAspect;
import de.adorsys.psd2.xs2a.web.controller.ConsentController;
import de.adorsys.psd2.xs2a.web.link.CreateConsentLinks;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

@Slf4j
@Aspect
@Component
public class CustomConsentAspect extends AbstractLinkAspect<ConsentController> {
    private final ScaApproachResolver scaApproachResolver;
    private final AuthorisationMethodDecider authorisationMethodDecider;
    private final RedirectLinkBuilder redirectLinkBuilder;
    private final RedirectIdService redirectIdService;
    private final MessageService messageService;
    private final AspspProfileServiceWrapper aspspProfileServiceWrapper;

    public CustomConsentAspect(ScaApproachResolver scaApproachResolver,
                               MessageService messageService,
                               AuthorisationMethodDecider authorisationMethodDecider,
                               RedirectLinkBuilder redirectLinkBuilder,
                               AspspProfileServiceWrapper aspspProfileServiceWrapper,
                               RedirectIdService redirectIdService) {
        super(messageService, aspspProfileServiceWrapper);
        this.scaApproachResolver = scaApproachResolver;
        this.authorisationMethodDecider = authorisationMethodDecider;
        this.redirectLinkBuilder = redirectLinkBuilder;
        this.redirectIdService = redirectIdService;
        this.messageService = messageService;
        this.aspspProfileServiceWrapper = aspspProfileServiceWrapper;
    }

    @AfterReturning(pointcut = "execution(* de.adorsys.psd2.xs2a.override.CustomXs2aConsentService.createAccountConsentsWithResponse(..)) && args( request, psuData, explicitPreferred)", returning = "result", argNames = "result,request,psuData,explicitPreferred")
    public ResponseObject<CreateConsentResponse> invokeCreateAccountConsentAspect(ResponseObject<CreateConsentResponse> result, CreateConsentReq request, PsuIdData psuData, boolean explicitPreferred) {
        if (!result.hasError()) {

            CreateConsentResponse body = result.getBody();
            boolean explicitMethod = authorisationMethodDecider.isExplicitMethod(explicitPreferred, body.isMultilevelScaRequired());
            boolean signingBasketModeActive = authorisationMethodDecider.isSigningBasketModeActive(explicitPreferred);

            body.setLinks(new CreateConsentLinks(customGetHttpUrl(), scaApproachResolver, body, redirectLinkBuilder,
                                                 redirectIdService,
                                                 explicitMethod, signingBasketModeActive,
                                                 customGetScaRedirectFlow()));
            return result;
        }
        return customEnrichErrorTextMessage(result);
    }

    private ScaRedirectFlow customGetScaRedirectFlow() {
        return aspspProfileServiceWrapper.getScaRedirectFlow();
    }

    private String customGetHttpUrl() {
        return aspspProfileServiceWrapper.isForceXs2aBaseLinksUrl()
                       ? aspspProfileServiceWrapper.getXs2aBaseLinksUrl()
                       : linkTo(customgetControllerClass()).toString();
    }

    private <R> ResponseObject<R> customEnrichErrorTextMessage(ResponseObject<R> response) {
        MessageError error = response.getError();
        TppMessageInformation tppMessage = error.getTppMessage();
        if (StringUtils.isBlank(tppMessage.getText())) {
            tppMessage.setText(messageService.getMessage(tppMessage.getMessageErrorCode().name()));
            error.setTppMessages(Collections.singleton(tppMessage));
        }
        return ResponseObject.<R>builder()
                       .fail(error)
                       .build();
    }

    @SuppressWarnings("unchecked")
    private Class<ConsentController> customgetControllerClass() {
        try {
            String className = ((ParameterizedType) this.getClass().getGenericSuperclass())
                                       .getActualTypeArguments()[0]
                                       .getTypeName();
            return (Class<ConsentController>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class isn't parametrized with generic type! Use <>");
        }
    }
}
