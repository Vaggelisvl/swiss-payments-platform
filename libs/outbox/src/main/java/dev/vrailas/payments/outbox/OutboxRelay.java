package dev.vrailas.payments.outbox;

import dev.vrailas.payments.contracts.Topology;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls unpublished outbox rows and publishes them to RabbitMQ with publisher confirms.
 *
 * <p>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so several instances of a service can run the
 * relay concurrently without publishing the same row twice at the same time. Delivery is at-least-once
 * (a crash between confirm and commit republishes), therefore consumers must be idempotent. An event
 * that the broker cannot route (no queue bound yet) is treated as a failure and retried later.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private record Row(UUID id, String routingKey, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final OutboxProperties properties;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transaction, RabbitTemplate rabbit,
                       OutboxProperties properties, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.properties = properties;
        this.clock = clock;
        this.published = Counter.builder("outbox.published").description("Events published to the broker").register(meters);
        this.failures = Counter.builder("outbox.publish.failures").description("Failed publish attempts").register(meters);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:500}")
    public void relay() {
        Integer count;
        do {
            count = transaction.execute(status -> publishBatch());
        } while (count != null && count >= properties.batchSize());
    }

    /** Publishes one batch inside the current transaction and returns the number of rows published. */
    int publishBatch() {
        List<Row> rows = jdbc.query(
                "SELECT id, routing_key, payload FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("routing_key"), rs.getString("payload")),
                properties.batchSize());
        int done = 0;
        for (Row row : rows) {
            try {
                publish(row);
            } catch (RuntimeException | InterruptedException | ExecutionException | TimeoutException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failures.increment();
                log.warn("Could not publish outbox event {} ({}): {}", row.id(), row.routingKey(), e.toString());
                break; // keep ordering; the rows already published in this batch are still marked below
            }
            jdbc.update("UPDATE outbox SET published_at = ? WHERE id = ?", Timestamp.from(clock.instant()), row.id());
            published.increment();
            done++;
        }
        return done;
    }

    private void publish(Row row) throws InterruptedException, ExecutionException, TimeoutException {
        Message message = MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(row.id().toString())
                .setHeader("type", row.routingKey())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(row.id().toString());
        rabbit.send(Topology.EXCHANGE, row.routingKey(), message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("broker nack: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("message is unroutable (no queue bound to " + row.routingKey() + ")");
        }
    }
}
