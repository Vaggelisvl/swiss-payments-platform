package dev.vrailas.payments.processor.application;

import dev.vrailas.payments.contracts.PaymentBooked;
import dev.vrailas.payments.contracts.PaymentRejected;
import dev.vrailas.payments.contracts.Topology;
import dev.vrailas.payments.processor.adapter.out.persistence.AccountEntity;
import dev.vrailas.payments.processor.adapter.out.persistence.AccountRepository;
import dev.vrailas.payments.processor.adapter.out.persistence.LedgerEntryEntity;
import dev.vrailas.payments.processor.adapter.out.persistence.LedgerEntryRepository;
import dev.vrailas.payments.processor.adapter.out.persistence.ProcessedPaymentEntity;
import dev.vrailas.payments.processor.adapter.out.persistence.ProcessedPaymentRepository;
import dev.vrailas.payments.processor.domain.PaymentOrder;
import dev.vrailas.payments.processor.domain.Rejection;
import dev.vrailas.payments.processor.domain.RuleEngine;
import dev.vrailas.payments.outbox.OutboxStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Processes one payment in a single database transaction: rules, ledger booking, idempotency marker and the
 * outcome event (via the outbox) commit or roll back together.
 *
 * <p>The debtor account row is locked first, so concurrent payments of one debtor are serialized and funds can
 * never be overspent; duplicates of the same message are skipped once the lock is held.
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final RuleEngine rules;
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final ProcessedPaymentRepository processed;
    private final OutboxStore outbox;
    private final Clock clock;
    private final MeterRegistry meters;

    public PaymentProcessor(RuleEngine rules, AccountRepository accounts, LedgerEntryRepository ledger,
                            ProcessedPaymentRepository processed, OutboxStore outbox, Clock clock, MeterRegistry meters) {
        this.rules = rules;
        this.accounts = accounts;
        this.ledger = ledger;
        this.processed = processed;
        this.outbox = outbox;
        this.clock = clock;
        this.meters = meters;
    }

    @Transactional
    public void process(PaymentOrder order) {
        Timer.Sample sample = Timer.start(meters);
        Optional<AccountEntity> debtor = accounts.findForUpdate(order.debtor().value());
        if (processed.existsById(order.id())) {
            log.debug("Payment {} already processed, skipping duplicate delivery", order.id());
            return;
        }
        Instant now = clock.instant();

        Optional<Rejection> rejection = rules.evaluate(order).or(() -> checkAccount(order, debtor));
        if (rejection.isPresent()) {
            reject(order, rejection.get(), now);
        } else {
            book(order, debtor.orElseThrow(), now);
        }
        sample.stop(meters.timer("payments.processing"));
    }

    private static Optional<Rejection> checkAccount(PaymentOrder order, Optional<AccountEntity> debtor) {
        if (debtor.isEmpty()) {
            return Optional.of(new Rejection(Rejection.UNKNOWN_DEBTOR, "Debtor account is not held by this bank"));
        }
        AccountEntity account = debtor.get();
        if (!account.getCurrency().equals(order.money().currency().name())) {
            return Optional.of(new Rejection(Rejection.CURRENCY_MISMATCH,
                    "Debtor account is held in " + account.getCurrency() + ", payment is in " + order.money().currency()));
        }
        if (!account.canCover(order.money().amount())) {
            return Optional.of(new Rejection(Rejection.INSUFFICIENT_FUNDS, "Insufficient funds on the debtor account"));
        }
        return Optional.empty();
    }

    private void book(PaymentOrder order, AccountEntity debtor, Instant now) {
        String currency = order.money().currency().name();
        debtor.debit(order.money().amount());
        ledger.save(new LedgerEntryEntity(order.id(), debtor.getIban(), order.money().amount().negate(), currency, now));
        // An internal creditor receives the money; an external one is settled outside this ledger.
        if (accounts.credit(order.creditor().value(), currency, order.money().amount()) > 0) {
            ledger.save(new LedgerEntryEntity(order.id(), order.creditor().value(), order.money().amount(), currency, now));
        }
        processed.save(new ProcessedPaymentEntity(order.id(), "BOOKED", null, now));
        outbox.append(order.id().toString(), Topology.PAYMENT_BOOKED, new PaymentBooked(order.id(), order.creditor().value(),
                order.money().amount(), currency, order.reference().type(), order.reference().value(), now));
        meters.counter("payments.booked").increment();
    }

    private void reject(PaymentOrder order, Rejection rejection, Instant now) {
        processed.save(new ProcessedPaymentEntity(order.id(), "REJECTED", rejection.code(), now));
        outbox.append(order.id().toString(), Topology.PAYMENT_REJECTED,
                new PaymentRejected(order.id(), rejection.code(), rejection.reason(), now));
        meters.counter("payments.rejected", "reason", rejection.code()).increment();
        log.info("Payment {} rejected: {}", order.id(), rejection.code());
    }
}
