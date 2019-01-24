package de.adorsys.ledgers.rest.client;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import feign.codec.Encoder;

@Configuration
public class FeignConfig {
	
	@Bean
	public Encoder feignEncoder() {
		ObjectMapper mapper = objectMapper();
		@SuppressWarnings("rawtypes")
		HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(mapper);
		ObjectFactory<HttpMessageConverters> objectFactory = () -> new HttpMessageConverters(jacksonConverter);
		return new SpringEncoder(objectFactory);
	}
	
    @SuppressWarnings("deprecation")
	public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//        objectMapper.registerModule(getDateTimeDeserializerModule());
//        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        objectMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
//        objectMapper.registerModule(new Jdk8Module()); // add support for Optionals
        objectMapper.registerModule(new JavaTimeModule()); // add support for java.time types
//        objectMapper.registerModule(new ParameterNamesModule()); // support for multiargs constructors
        return objectMapper;
    }
}
