package de.adorsys.aspsp.xs2a.embedded.connector;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value = {java.lang.annotation.ElementType.TYPE})
@Documented
@Import({
        LedgersXS2AConnectorEmbeddedConfiguration.class
})
public @interface EnableLedgersXS2AConnectorEmbedded {
}
