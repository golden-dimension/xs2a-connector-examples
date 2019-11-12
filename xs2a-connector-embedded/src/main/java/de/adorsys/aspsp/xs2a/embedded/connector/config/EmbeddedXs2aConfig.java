package de.adorsys.aspsp.xs2a.embedded.connector.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ComponentScan(basePackages = "de.adorsys.psd2.report")
@EnableTransactionManagement
@EntityScan({"de.adorsys.psd2.consent.domain", "de.adorsys.psd2.event.persist.entity"})
@EnableJpaRepositories(basePackages = {"de.adorsys.psd2.consent.repository", "de.adorsys.psd2.event"})
public class EmbeddedXs2aConfig {
}
