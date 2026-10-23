package dev.vrailas.payments.assistant.config;

import dev.vrailas.payments.assistant.adapter.out.gemini.GeminiExplanationModel;
import dev.vrailas.payments.assistant.domain.ExplanationModel;
import dev.vrailas.payments.assistant.domain.ExplanationUnavailableException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class AppConfig {

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
