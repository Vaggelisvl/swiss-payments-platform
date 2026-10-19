package dev.vrailas.payments.reconciliation.adapter.out.persistence;

import dev.vrailas.payments.reconciliation.domain.Credit;
import dev.vrailas.payments.reconciliation.domain.CreditResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credit_entry")
public class CreditEntryEntity {

    @Id
    @Column(name = "entry_id")
    private String entryId;
    @Column(nullable = false)
    private String source;
    private String reference;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency;
    @Column(name = "booked_on")
    private LocalDate bookedOn;
    @Column(name = "invoice_id")
    private UUID invoiceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditResult result;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditEntryEntity() {
    }

    public CreditEntryEntity(Credit credit, UUID invoiceId, CreditResult result, Instant now) {
        this.entryId = credit.entryId();
        this.source = credit.source().name();
        this.reference = credit.reference();
        this.amount = credit.amount();
        this.currency = credit.currency();
        this.bookedOn = credit.bookedOn();
        this.invoiceId = invoiceId;
        this.result = result;
        this.createdAt = now;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getSource() {
        return source;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getBookedOn() {
        return bookedOn;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public CreditResult getResult() {
        return result;
    }
}
