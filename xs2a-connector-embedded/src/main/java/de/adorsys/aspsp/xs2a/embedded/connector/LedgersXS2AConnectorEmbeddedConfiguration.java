package de.adorsys.aspsp.xs2a.embedded.connector;

import de.adorsys.aspsp.xs2a.connector.EnableLedgersXS2AConnector;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableLedgersXS2AConnector
public class LedgersXS2AConnectorEmbeddedConfiguration {
}
