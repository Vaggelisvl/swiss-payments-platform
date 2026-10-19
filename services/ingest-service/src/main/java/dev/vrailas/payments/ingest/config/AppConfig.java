package dev.vrailas.payments.ingest.config;

import dev.vrailas.payments.contracts.Topology;
import dev.vrailas.payments.outbox.Queues;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class AppConfig {

    static final String BOOKED_QUEUE = "ingest.payment-booked";
    static final String REJECTED_QUEUE = "ingest.payment-rejected";

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
        return Queues.consumerQueue(BOOKED_QUEUE, Topology.PAYMENT_BOOKED);
    }

    @Bean
    Declarables rejectedQueue() {
        return Queues.consumerQueue(REJECTED_QUEUE, Topology.PAYMENT_REJECTED);
    }
}
