package dev.vrailas.payments.processor.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity {

    @Id
    private UUID id;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(nullable = false)
    private String iban;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private String currency;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {
    }

    public LedgerEntryEntity(UUID paymentId, String iban, BigDecimal signedAmount, String currency, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.iban = iban;
        this.amount = signedAmount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getIban() {
        return iban;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
