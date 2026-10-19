package dev.vrailas.payments.ingest.adapter.in.messaging;

import dev.vrailas.payments.contracts.PaymentBooked;
import dev.vrailas.payments.contracts.PaymentRejected;
import dev.vrailas.payments.ingest.application.PaymentStatusService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class PaymentOutcomeListener {

    private final PaymentStatusService statusService;

    PaymentOutcomeListener(PaymentStatusService statusService) {
        this.statusService = statusService;
    }

    @RabbitListener(queues = "ingest.payment-booked")
    void onBooked(PaymentBooked event) {
        statusService.onBooked(event);
    }

    @RabbitListener(queues = "ingest.payment-rejected")
    void onRejected(PaymentRejected event) {
        statusService.onRejected(event);
    }
}
