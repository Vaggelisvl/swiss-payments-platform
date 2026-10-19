package dev.vrailas.payments.ingest.application;

import dev.vrailas.payments.contracts.PaymentReceived;
import dev.vrailas.payments.contracts.Topology;
import dev.vrailas.payments.ingest.adapter.out.persistence.PaymentEntity;
import dev.vrailas.payments.ingest.adapter.out.persistence.PaymentRepository;
import dev.vrailas.payments.ingest.domain.PaymentInstruction;
import dev.vrailas.payments.outbox.OutboxStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Accepts a payment exactly once per idempotency key. The payment row and the {@code PaymentReceived}
 * event are written in one transaction (transactional outbox), so a crash can never lose or invent an event.
 */
@Service
public class PaymentIntakeService {

    /** {@code replayed} is true when the key was already known and the original payment is returned. */
    public record Outcome(PaymentEntity payment, boolean replayed) {
    }

    private final PaymentRepository payments;
    private final OutboxStore outbox;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentIntakeService(PaymentRepository payments, OutboxStore outbox, PlatformTransactionManager transactionManager, Clock clock) {
        this.payments = payments;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Outcome accept(String idempotencyKey, PaymentInstruction instruction) {
        try {
            return transaction.execute(status -> payments.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> replay(existing, instruction))
                    .orElseGet(() -> store(idempotencyKey, instruction)));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // A parallel request with the same key won the race on the unique constraint: treat ours as a replay.
            return transaction.execute(status -> payments.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> replay(existing, instruction))
                    .orElseThrow(() -> concurrentInsert));
        }
    }

    private Outcome store(String idempotencyKey, PaymentInstruction instruction) {
        PaymentEntity payment = payments.saveAndFlush(PaymentEntity.receive(UUID.randomUUID(), idempotencyKey, instruction, clock.instant()));
        outbox.append(payment.getId().toString(), Topology.PAYMENT_RECEIVED, new PaymentReceived(
                payment.getId(),
                payment.getDebtorIban(),
                payment.getCreditorIban(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getReferenceType(),
                payment.getReference(),
                payment.getRemittanceInformation(),
                payment.getCreatedAt()));
        return new Outcome(payment, false);
    }

    private Outcome replay(PaymentEntity existing, PaymentInstruction instruction) {
        if (!existing.getFingerprint().equals(instruction.fingerprint())) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        return new Outcome(existing, true);
    }
}
