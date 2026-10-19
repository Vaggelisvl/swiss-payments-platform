package dev.vrailas.payments.processor.adapter.in.messaging;

import dev.vrailas.payments.contracts.PaymentReceived;
import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import dev.vrailas.payments.processor.application.PaymentProcessor;
import dev.vrailas.payments.processor.domain.PaymentOrder;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code PaymentReceived}. A message that violates the domain rules is a poison message: the thrown
 * exception sends it, after the configured retries, to the dead-letter queue instead of blocking the queue.
 */
@Component
class PaymentReceivedListener {

    private final PaymentProcessor processor;

    PaymentReceivedListener(PaymentProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "processor.payment-received")
    void onPaymentReceived(PaymentReceived event) {
        MDC.put("paymentId", event.paymentId().toString());
        try {
            processor.process(new PaymentOrder(
                    event.paymentId(),
                    new Iban(event.debtorIban()),
                    new Iban(event.creditorIban()),
                    new Money(event.amount(), Currency.valueOf(event.currency())),
                    PaymentReference.of(event.referenceType(), event.reference())));
        } finally {
            MDC.remove("paymentId");
        }
    }
}
