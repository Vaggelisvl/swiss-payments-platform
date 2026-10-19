package dev.vrailas.payments.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vrailas.payments.contracts.Topology;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.outbox.Queues;
import dev.vrailas.payments.processor.domain.PaymentRule;
import dev.vrailas.payments.processor.domain.RuleEngine;
import dev.vrailas.payments.processor.domain.Rules;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
class AppConfig {

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    Declarables exchanges() {
        return Queues.exchanges();
    }

    @Bean
    Declarables receivedQueue() {
        return Queues.consumerQueue("processor.payment-received", Topology.PAYMENT_RECEIVED);
    }

    /** Rule order matters: cheap structural checks first. */
    @Bean
    RuleEngine ruleEngine(ProcessorProperties properties) {
        Set<Iban> blocked = properties.denylist().stream().map(Iban::new).collect(Collectors.toUnmodifiableSet());
        List<PaymentRule> rules = List.of(
                Rules.differentAccounts(),
                Rules.amountLimit(properties.maxAmount()),
                Rules.denylist(blocked));
        return new RuleEngine(rules);
    }
}
