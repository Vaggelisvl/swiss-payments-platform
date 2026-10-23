package dev.vrailas.payments.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("assistant.gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl, Duration timeout) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
