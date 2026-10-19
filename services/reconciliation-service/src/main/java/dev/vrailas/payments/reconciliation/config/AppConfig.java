package dev.vrailas.payments.reconciliation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vrailas.payments.contracts.Topology;
import dev.vrailas.payments.outbox.Queues;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    Declarables exchanges() {
        return Queues.exchanges();
    }

    @Bean
    Declarables bookedQueue() {
        return Queues.consumerQueue("reconciliation.payment-booked", Topology.PAYMENT_BOOKED);
    }
}
