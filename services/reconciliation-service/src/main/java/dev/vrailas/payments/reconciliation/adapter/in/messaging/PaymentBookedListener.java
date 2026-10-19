package dev.vrailas.payments.reconciliation.adapter.in.messaging;

import dev.vrailas.payments.contracts.PaymentBooked;
import dev.vrailas.payments.reconciliation.application.ReconciliationService;
import dev.vrailas.payments.reconciliation.domain.Credit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/** Every booked payment is an incoming credit for its creditor, matched by QR / creditor reference. */
@Component
class PaymentBookedListener {

    private final ReconciliationService reconciliation;

    PaymentBookedListener(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @RabbitListener(queues = "reconciliation.payment-booked")
    void onPaymentBooked(PaymentBooked event) {
        String reference = event.reference() == null || event.reference().isBlank() ? null : event.reference();
        reconciliation.reconcile(new Credit("event:" + event.paymentId(), Credit.Source.EVENT, reference, event.amount(),
                event.currency(), event.bookedAt().atZone(ZoneOffset.UTC).toLocalDate()));
    }
}
