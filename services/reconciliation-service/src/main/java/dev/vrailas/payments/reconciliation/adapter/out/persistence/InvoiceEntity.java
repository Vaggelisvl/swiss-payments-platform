package dev.vrailas.payments.reconciliation.adapter.out.persistence;

import dev.vrailas.payments.reconciliation.domain.CreditResult;
import dev.vrailas.payments.reconciliation.domain.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An invoice announced with a QR or creditor reference; payments are applied to it by {@link #receive}. */
@Entity
@Table(name = "invoice")
public class InvoiceEntity {

    @Id
    private UUID id;
    @Column(nullable = false, updatable = false, unique = true)
    private String reference;
    @Column(name = "reference_type", nullable = false, updatable = false)
    private String referenceType;
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, updatable = false)
    private String currency;
    @Column(name = "paid_amount", nullable = false)
    private BigDecimal paidAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InvoiceEntity() {
    }

    public InvoiceEntity(UUID id, String referenceType, String reference, BigDecimal amount, String currency, Instant now) {
        this.id = id;
        this.referenceType = referenceType;
        this.reference = reference;
        this.amount = amount;
        this.currency = currency;
        this.paidAmount = BigDecimal.ZERO.setScale(2);
        this.status = InvoiceStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Applies a credit and returns how it relates to the invoice. A wrong currency leaves the invoice untouched. */
    public CreditResult receive(BigDecimal creditAmount, String creditCurrency, Instant now) {
        if (!currency.equals(creditCurrency)) {
            return CreditResult.CURRENCY_MISMATCH;
        }
        paidAmount = paidAmount.add(creditAmount);
        updatedAt = now;
        int comparison = paidAmount.compareTo(amount);
        if (comparison < 0) {
            status = InvoiceStatus.PARTIALLY_PAID;
            return CreditResult.PARTIAL;
        }
        if (comparison == 0) {
            status = InvoiceStatus.PAID;
            return CreditResult.MATCHED;
        }
        status = InvoiceStatus.OVERPAID;
        return CreditResult.OVERPAID;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }
}
