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

import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import org.springframework.stereotype.Service;

@Service
public class ScaResponseMapper {

    public SCAPaymentResponseTO mapToScaPaymentResponse(GlobalScaResponseTO globalScaResponseTO) {
        SCAPaymentResponseTO paymentResponseTO = new SCAPaymentResponseTO();

        paymentResponseTO.setPaymentId(globalScaResponseTO.getOperationObjectId());
        paymentResponseTO.setAuthorisationId(globalScaResponseTO.getAuthorisationId());
        paymentResponseTO.setScaStatus(globalScaResponseTO.getScaStatus());
        paymentResponseTO.setScaMethods(globalScaResponseTO.getScaMethods());
        paymentResponseTO.setBearerToken(globalScaResponseTO.getBearerToken());
        paymentResponseTO.setAuthConfirmationCode(globalScaResponseTO.getAuthConfirmationCode());
        paymentResponseTO.setChallengeData(globalScaResponseTO.getChallengeData());
        paymentResponseTO.setExpiresInSeconds(globalScaResponseTO.getExpiresInSeconds());
        paymentResponseTO.setMultilevelScaRequired(globalScaResponseTO.isMultilevelScaRequired());
        paymentResponseTO.setPsuMessage(globalScaResponseTO.getPsuMessage());

        return paymentResponseTO;
    }

    public SCAConsentResponseTO mapToScaConsentResponse(GlobalScaResponseTO globalScaResponseTO) {
        SCAConsentResponseTO consentResponseTO = new SCAConsentResponseTO();

        consentResponseTO.setConsentId(globalScaResponseTO.getOperationObjectId());
        consentResponseTO.setAuthorisationId(globalScaResponseTO.getAuthorisationId());
        consentResponseTO.setScaStatus(globalScaResponseTO.getScaStatus());
        consentResponseTO.setScaMethods(globalScaResponseTO.getScaMethods());
        consentResponseTO.setBearerToken(globalScaResponseTO.getBearerToken());
        consentResponseTO.setAuthConfirmationCode(globalScaResponseTO.getAuthConfirmationCode());
        consentResponseTO.setChallengeData(globalScaResponseTO.getChallengeData());
        consentResponseTO.setExpiresInSeconds(globalScaResponseTO.getExpiresInSeconds());
        consentResponseTO.setMultilevelScaRequired(globalScaResponseTO.isMultilevelScaRequired());
        consentResponseTO.setPsuMessage(globalScaResponseTO.getPsuMessage());

        return consentResponseTO;
    }

}
