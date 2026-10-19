package dev.vrailas.payments.reconciliation.application;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import dev.vrailas.payments.reconciliation.adapter.out.persistence.InvoiceEntity;
import dev.vrailas.payments.reconciliation.adapter.out.persistence.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional
    public InvoiceEntity open(String referenceType, String reference, BigDecimal amount, Currency currency) {
        PaymentReference parsed = PaymentReference.of(referenceType, reference);
        if (parsed.value().isEmpty()) {
            throw new DomainViolationException("an invoice needs a QRR or SCOR reference");
        }
        Money money = new Money(amount, currency);
        if (invoices.findByReference(parsed.value()).isPresent()) {
            throw new InvoiceAlreadyExistsException(parsed.value());
        }
        return invoices.save(new InvoiceEntity(UUID.randomUUID(), parsed.type(), parsed.value(), money.amount(), currency.name(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public InvoiceEntity get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public InvoiceEntity getByReference(String reference) {
        String normalized = reference.replaceAll("\\s+", "");
        return invoices.findByReference(normalized).orElseThrow(() -> new InvoiceNotFoundException(normalized));
    }
}
