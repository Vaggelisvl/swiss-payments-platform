package dev.vrailas.payments.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Appends integration events to the outbox table. Must be called inside the business transaction
 * ({@link Propagation#MANDATORY}) so that state change and event are committed atomically.
 */
public class OutboxStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxStore(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateId, String routingKey, Object event) {
        jdbc.update("INSERT INTO outbox (id, aggregate_id, routing_key, payload, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), aggregateId, routingKey, toJson(event), Timestamp.from(clock.instant()));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Event is not serializable: " + event.getClass().getName(), e);
        }
    }
}
