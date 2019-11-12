package de.adorsys.aspsp.xs2a.embedded.connector;

import de.adorsys.aspsp.xs2a.connector.EnableLedgersXS2AConnector;
import de.adorsys.aspsp.xs2a.embedded.connector.config.EmbeddedXs2aConfig;
import de.adorsys.aspsp.xs2a.embedded.connector.spi.web.Xs2aInterfaceConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan
@EnableLedgersXS2AConnector
@Import({Xs2aInterfaceConfig.class, EmbeddedXs2aConfig.class})
public class LedgersXS2AConnectorEmbeddedConfiguration {
}
