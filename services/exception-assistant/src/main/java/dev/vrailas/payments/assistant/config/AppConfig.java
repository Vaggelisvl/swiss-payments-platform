package dev.vrailas.payments.assistant.config;

import dev.vrailas.payments.assistant.adapter.in.web.AccessTokenFilter;
import dev.vrailas.payments.assistant.adapter.out.gemini.GeminiExplanationModel;
import dev.vrailas.payments.assistant.domain.ExplanationModel;
import dev.vrailas.payments.assistant.domain.ExplanationUnavailableException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    @ConditionalOnExpression("!'${assistant.access-token:}'.isEmpty()")
    FilterRegistrationBean<AccessTokenFilter> accessTokenFilter(@Value("${assistant.access-token}") String token) {
        return new FilterRegistrationBean<>(new AccessTokenFilter(token));
    }

    @Bean
    ExplanationModel explanationModel(GeminiProperties properties, ObjectMapper objectMapper) {
        if (!properties.enabled()) {
            return (exceptionCase, hint) -> {
                throw new ExplanationUnavailableException("GEMINI_API_KEY is not set");
            };
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        RestClient client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .build();
        return new GeminiExplanationModel(client, properties.model(), objectMapper);
    }
}
