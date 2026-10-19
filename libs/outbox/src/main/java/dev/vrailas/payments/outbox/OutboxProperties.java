package dev.vrailas.payments.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Tuning of the outbox relay ({@code outbox.relay.*}). */
@ConfigurationProperties(prefix = "outbox.relay")
public record OutboxProperties(Integer batchSize, Duration confirmTimeout) {

    public OutboxProperties {
        batchSize = batchSize == null ? 50 : batchSize;
        confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
    }
}
