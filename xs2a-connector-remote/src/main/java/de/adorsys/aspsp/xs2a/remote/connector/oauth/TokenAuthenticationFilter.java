package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.exception.MessageCategory;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageBuilder;
import de.adorsys.psd2.xs2a.web.filter.AbstractXs2aFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TokenAuthenticationFilter extends AbstractXs2aFilter {
    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String CONSENT_ENP_ENDING = "consents";
    private static final String FUNDS_CONF_ENP_ENDING = "funds-confirmations";
    private final String oauthModeHeaderName;
    private final TppErrorMessageBuilder tppErrorMessageBuilder;
    private final TokenValidationService tokenValidationService;
    private final AspspProfileService aspspProfileService;
    private final OauthDataHolder oauthDataHolder;

    public TokenAuthenticationFilter(@Value("${oauth.header-name:X-OAUTH-PREFERRED}") String oauthModeHeaderName,
                                     TppErrorMessageBuilder tppErrorMessageBuilder,
                                     TokenValidationService tokenValidationService,
                                     AspspProfileService aspspProfileService,
                                     OauthDataHolder oauthDataHolder) {
        this.oauthModeHeaderName = oauthModeHeaderName;
        this.tppErrorMessageBuilder = tppErrorMessageBuilder;
        this.tokenValidationService = tokenValidationService;
        this.aspspProfileService = aspspProfileService;
        this.oauthDataHolder = oauthDataHolder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain) throws IOException, ServletException {
        String oauthHeader = request.getHeader(oauthModeHeaderName);
        boolean isOauthMode = StringUtils.isNotBlank(oauthHeader);

        if (!isOauthMode) {
            chain.doFilter(request, response);
            return;
        }

        if (!aspspProfileService.getScaApproaches().contains(ScaApproach.OAUTH)) {
            log.info("Token authentication error: OAUTH SCA approach is not supported in the profile");
            enrichError(response, HttpServletResponse.SC_BAD_REQUEST, MessageErrorCode.FORMAT_ERROR);
            return;
        }

        Optional<OauthType> oauthTypeOptional = OauthType.getByValue(oauthHeader);
        if (!oauthTypeOptional.isPresent()) {
            log.info("Token authentication error: unknown OAuth type {}", oauthHeader);
            enrichError(response, HttpServletResponse.SC_BAD_REQUEST, MessageErrorCode.FORMAT_ERROR);
            return;
        }

        OauthType oauthType = oauthTypeOptional.get();
        String bearerToken = resolveBearerToken(request);
        oauthDataHolder.setOauthTypeAndToken(oauthType, bearerToken);

        if (isTokenRequired(oauthType, request.getServletPath()) && isTokenInvalid(bearerToken)) {
            log.info("Token authentication error: token is invalid");
            enrichError(response, HttpServletResponse.SC_FORBIDDEN, MessageErrorCode.TOKEN_INVALID);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isTokenRequired(OauthType oauthType, String servletPath) {
        if (oauthType == OauthType.PRE_STEP) {
            return true;
        }

        String trimmedServletPath = trimEndingSlash(servletPath);
        if (trimmedServletPath.endsWith(CONSENT_ENP_ENDING) || trimmedServletPath.endsWith(FUNDS_CONF_ENP_ENDING)) {
            return false;
        } else {
            Set<String> supportedProducts = aspspProfileService.getAspspSettings().getPis().getSupportedPaymentTypeAndProductMatrix().values().stream()
                                                    .flatMap(Collection::stream).collect(Collectors.toSet());
            return supportedProducts.stream().noneMatch(trimmedServletPath::endsWith);
        }
    }

    private boolean isTokenInvalid(String bearerToken) {
        BearerTokenTO token = tokenValidationService.validate(bearerToken);
        return token == null;
    }

    private void enrichError(HttpServletResponse response, int status, MessageErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().print(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, errorCode).toString());
    }

    private String resolveBearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                       .filter(StringUtils::isNotBlank)
                       .filter(t -> StringUtils.startsWithIgnoreCase(t, BEARER_TOKEN_PREFIX))
                       .map(t -> StringUtils.substringAfter(t, BEARER_TOKEN_PREFIX))
                       .orElse(null);
    }

    private String trimEndingSlash(String input) {
        String result = input;

        while (StringUtils.endsWith(result, "/")) {
            result = StringUtils.removeEnd(result, "/");
        }

        return result;
    }
}