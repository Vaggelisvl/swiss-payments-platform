package dev.vrailas.payments.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Registers the outbox store and relay in every service that has the library on its classpath. */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock outboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    OutboxStore outboxStore(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        return new OutboxStore(jdbc, objectMapper, clock);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, RabbitTemplate rabbit,
                            OutboxProperties properties, Clock clock, MeterRegistry meters) {
        return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), rabbit, properties, clock, meters);
    }
}
