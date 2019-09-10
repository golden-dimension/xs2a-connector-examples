package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.nio.charset.Charset;
import java.util.Collections;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class FeignExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FeignExceptionHandler.class);

    static final String REQUEST_WAS_FAILED_MESSAGE = "Request was failed";

    private FeignExceptionHandler() {
    }

    static TppMessage getFailureMessage(FeignException e, MessageErrorCode errorCode, String errorMessage) {
        logger.error(e.getMessage(), e);

        switch (HttpStatus.valueOf(e.status())) {
            case INTERNAL_SERVER_ERROR:
                return new TppMessage(MessageErrorCode.INTERNAL_SERVER_ERROR, REQUEST_WAS_FAILED_MESSAGE);
            case UNAUTHORIZED:
                return new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID, errorMessage);
            default:
                return new TppMessage(errorCode, errorMessage);
        }
    }

    static TppMessage getFailureMessage(FeignException e, MessageErrorCode errorCode, String errorMessageAspsp, String errorMessage) {
        return StringUtils.isBlank(errorMessageAspsp) || HttpStatus.valueOf(e.status()) == BAD_REQUEST && errorCode == MessageErrorCode.PAYMENT_FAILED
                       ? getFailureMessage(e, errorCode, errorMessage)
                       : getFailureMessage(e, errorCode, errorMessageAspsp);
    }

    public static FeignException getException(HttpStatus httpStatus, String message) {
        return FeignException.errorStatus(message, error(httpStatus));
    }

    static Response error(HttpStatus httpStatus) {
        return getBuilderTemplate(httpStatus)
                       .build();
    }

    static Response error(HttpStatus httpStatus, String body) {
        return getBuilderTemplate(httpStatus)
                       .body(body, Charset.forName("utf-8"))
                       .build();
    }

    private static Response.Builder getBuilderTemplate(HttpStatus httpStatus) {
        return Response.builder()
                       .status(httpStatus.value())
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap());
    }
}
