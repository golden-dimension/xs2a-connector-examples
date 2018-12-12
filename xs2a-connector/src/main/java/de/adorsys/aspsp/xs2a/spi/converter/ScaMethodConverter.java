/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.spi.converter;

import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ScaMethodConverter {

    @Mapping(source = "scaMethod", target = "authenticationType")
    @Mapping(source = "methodValue", target = "name")
    @Mapping(source = "id", target = "authenticationMethodId")
    SpiAuthenticationObject toSpiAuthenticationObject(SCAMethodTO method);

    List<SpiAuthenticationObject> toSpiAuthenticationObjectList(List<SCAMethodTO> methods);
}