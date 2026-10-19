package dev.vrailas.payments.outbox;

import dev.vrailas.payments.contracts.Topology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/** Declares a durable consumer queue with its dead-letter queue, bound to the payments exchange. */
public final class Queues {

    private Queues() {
    }

    public static Declarables consumerQueue(String name, String... routingKeys) {
        TopicExchange exchange = new TopicExchange(Topology.EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(Topology.DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(name)
                .deadLetterExchange(Topology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(name + ".dlq").build();

        List<Declarable> declarables = new ArrayList<>(List.of(exchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(name)));
        for (String key : routingKeys) {
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(key);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }

    /** Declares only the exchanges, for services that publish but do not consume. */
    public static Declarables exchanges() {
        return new Declarables(new TopicExchange(Topology.EXCHANGE, true, false),
                new DirectExchange(Topology.DEAD_LETTER_EXCHANGE, true, false));
    }
}
