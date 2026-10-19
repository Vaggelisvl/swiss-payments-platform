package dev.vrailas.payments.ingest.application;

import dev.vrailas.payments.contracts.PaymentBooked;
import dev.vrailas.payments.contracts.PaymentRejected;
import dev.vrailas.payments.ingest.adapter.out.persistence.PaymentEntity;
import dev.vrailas.payments.ingest.adapter.out.persistence.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Applies processing outcomes to the payment so that clients can query the final state. Idempotent. */
@Service
public class PaymentStatusService {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusService.class);

    private final PaymentRepository payments;
    private final Clock clock;

    public PaymentStatusService(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public void onBooked(PaymentBooked event) {
        payments.findById(event.paymentId()).ifPresentOrElse(
                payment -> logIgnored(payment.markBooked(clock.instant()), event.paymentId()),
                () -> logUnknown(event.paymentId()));
    }

    @Transactional
    public void onRejected(PaymentRejected event) {
        payments.findById(event.paymentId()).ifPresentOrElse(
                payment -> logIgnored(payment.markRejected(event.reasonCode(), event.reason(), clock.instant()), event.paymentId()),
                () -> logUnknown(event.paymentId()));
    }

    @Transactional(readOnly = true)
    public PaymentEntity get(UUID id) {
        return payments.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private static void logIgnored(boolean applied, UUID id) {
        if (!applied) {
            log.debug("Outcome for payment {} ignored: already in a final state", id);
        }
    }

    private static void logUnknown(UUID id) {
        log.warn("Outcome for unknown payment {} ignored", id);
    }
}
