package dev.vrailas.payments.reconciliation.application;

import dev.vrailas.payments.reconciliation.adapter.out.persistence.CreditEntryEntity;
import dev.vrailas.payments.reconciliation.adapter.out.persistence.CreditEntryRepository;
import dev.vrailas.payments.reconciliation.adapter.out.persistence.InvoiceEntity;
import dev.vrailas.payments.reconciliation.adapter.out.persistence.InvoiceRepository;
import dev.vrailas.payments.reconciliation.domain.Credit;
import dev.vrailas.payments.reconciliation.domain.CreditResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Books incoming credits against invoices. Each credit is applied exactly once: a repeated entry id (event
 * redelivery, re-uploaded camt.054 file) is detected and reported as a duplicate without changing anything.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /** {@code duplicate} is true when the entry had already been booked; {@code result} is then the original result. */
    public record Outcome(CreditResult result, boolean duplicate) {
    }

    private final InvoiceRepository invoices;
    private final CreditEntryRepository credits;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final MeterRegistry meters;

    public ReconciliationService(InvoiceRepository invoices, CreditEntryRepository credits, PlatformTransactionManager transactionManager,
                                 Clock clock, MeterRegistry meters) {
        this.invoices = invoices;
        this.credits = credits;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.meters = meters;
    }

    public Outcome reconcile(Credit credit) {
        try {
            return transaction.execute(status -> book(credit));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Same entry booked by a parallel request: the other transaction won, ours is a duplicate.
            return transaction.execute(status -> credits.findById(credit.entryId())
                    .map(existing -> new Outcome(existing.getResult(), true))
                    .orElseThrow(() -> concurrentInsert));
        }
    }

    private Outcome book(Credit credit) {
        Optional<InvoiceEntity> invoice = credit.reference() == null
                ? Optional.empty()
                : invoices.findForUpdateByReference(credit.reference());
        Optional<CreditEntryEntity> existing = credits.findById(credit.entryId());
        if (existing.isPresent()) {
            meters.counter("reconciliation.duplicates", "source", credit.source().name()).increment();
            return new Outcome(existing.get().getResult(), true);
        }

        CreditResult result = invoice.map(i -> i.receive(credit.amount(), credit.currency(), clock.instant())).orElse(CreditResult.UNMATCHED);
        credits.saveAndFlush(new CreditEntryEntity(credit, invoice.filter(i -> result != CreditResult.CURRENCY_MISMATCH).map(InvoiceEntity::getId).orElse(null),
                result, clock.instant()));
        meters.counter("reconciliation.credits", "result", result.name()).increment();
        if (result.needsReview()) {
            log.warn("Credit {} needs manual review: {}", credit.entryId(), result);
        }
        return new Outcome(result, false);
    }

    public List<CreditEntryEntity> needingReview() {
        return credits.findByResultInOrderByCreatedAt(List.of(CreditResult.UNMATCHED, CreditResult.CURRENCY_MISMATCH, CreditResult.OVERPAID));
    }
}
