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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AspspConsentDataService {

    private final ObjectMapper objectMapper;

    /**
     * Default storage, makes sure there is a bearer token in the response object.
     */
    public byte[] store(SCAResponseTO response) {
        return store(response, true);
    }

    public byte[] store(SCAResponseTO response, boolean checkCredentials) {
        if (checkCredentials && response.getBearerToken() == null) {
            throw new IllegalStateException("Missing credentials, response must contain a bearer token by default.");
        }
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    public <T extends SCAResponseTO> T response(byte[] aspspConsentData, Class<T> klass) {
        return response(aspspConsentData, klass, true);
    }

    public SCAResponseTO response(byte[] aspspConsentData) {
        return response(aspspConsentData, true);
    }

    public SCAResponseTO response(byte[] aspspConsentData, boolean checkCredentials) {
        try {
            SCAResponseTO sca = fromBytes(aspspConsentData);
            checkBearerTokenPresent(checkCredentials, sca);
            return sca;
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    public <T extends SCAResponseTO> T response(byte[] aspspConsentData, Class<T> klass, boolean checkCredentials) {
        try {
            T sca = fromBytes(aspspConsentData, klass);
            checkBearerTokenPresent(checkCredentials, sca);
            return sca;
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private <T extends SCAResponseTO> T fromBytes(byte[] tokenBytes, Class<T> klass) throws IOException {
        String type = readType(tokenBytes);
        if(!klass.getSimpleName().equals(type)) {
            return null;
        }
        return objectMapper.readValue(tokenBytes, klass);
    }

    private <T extends SCAResponseTO> void checkBearerTokenPresent(boolean checkCredentials, T sca) {
        if (checkCredentials && sca.getBearerToken() == null) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "Missing credentials. Expecting a bearer token in the consent data object.");
        }
    }

    private String readType(byte[] tokenBytes) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(tokenBytes);
        JsonNode objectType = jsonNode.get("objectType");
        if (objectType == null) {
            return null;
        }
        return objectType.textValue();
    }

    private SCAResponseTO fromBytes(byte[] tokenBytes) throws IOException {
        String type = readType(tokenBytes);
        if (SCAConsentResponseTO.class.getSimpleName().equals(type)) {
            return objectMapper.readValue(tokenBytes, SCAConsentResponseTO.class);
        } else if (SCALoginResponseTO.class.getSimpleName().equals(type)) {
            return objectMapper.readValue(tokenBytes, SCALoginResponseTO.class);
        } else if (SCAPaymentResponseTO.class.getSimpleName().equals(type)) {
            return objectMapper.readValue(tokenBytes, SCAPaymentResponseTO.class);
        } else {
            return null;
        }
    }
}
