package de.adorsys.aspsp.xs2a.connector;

import de.adorsys.ledgers.keycloak.client.impl.KeycloakTokenServiceImpl;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = {LedgersXS2AConnectorBasePackage.class, KeycloakTokenServiceImpl.class})
public class LedgersXS2AConnectorConfiguration {
}
