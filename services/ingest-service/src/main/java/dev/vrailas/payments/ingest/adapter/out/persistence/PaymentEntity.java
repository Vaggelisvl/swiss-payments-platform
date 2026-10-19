package dev.vrailas.payments.ingest.adapter.out.persistence;

import dev.vrailas.payments.ingest.domain.PaymentInstruction;
import dev.vrailas.payments.ingest.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private UUID id;
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;
    @Column(nullable = false, updatable = false)
    private String fingerprint;
    @Column(name = "debtor_iban", nullable = false, updatable = false)
    private String debtorIban;
    @Column(name = "creditor_iban", nullable = false, updatable = false)
    private String creditorIban;
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, updatable = false)
    private String currency;
    @Column(name = "reference_type", nullable = false, updatable = false)
    private String referenceType;
    @Column(nullable = false, updatable = false)
    private String reference;
    @Column(name = "remittance_information", nullable = false, updatable = false)
    private String remittanceInformation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    @Column(name = "rejection_code")
    private String rejectionCode;
    @Column(name = "rejection_reason")
    private String rejectionReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {
    }

    public static PaymentEntity receive(UUID id, String idempotencyKey, PaymentInstruction instruction, Instant now) {
        PaymentEntity e = new PaymentEntity();
        e.id = id;
        e.idempotencyKey = idempotencyKey;
        e.fingerprint = instruction.fingerprint();
        e.debtorIban = instruction.debtor().value();
        e.creditorIban = instruction.creditor().value();
        e.amount = instruction.money().amount();
        e.currency = instruction.money().currency().name();
        e.referenceType = instruction.reference().type();
        e.reference = instruction.reference().value();
        e.remittanceInformation = instruction.remittanceInformation();
        e.status = PaymentStatus.RECEIVED;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /** Only a payment that is still RECEIVED can reach a final state; replays of outcome events are ignored. */
    public boolean markBooked(Instant now) {
        return transition(PaymentStatus.BOOKED, null, null, now);
    }

    public boolean markRejected(String code, String reason, Instant now) {
        return transition(PaymentStatus.REJECTED, code, reason, now);
    }

    private boolean transition(PaymentStatus target, String code, String reason, Instant now) {
        if (status != PaymentStatus.RECEIVED) {
            return false;
        }
        status = target;
        rejectionCode = code;
        rejectionReason = reason;
        updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getDebtorIban() {
        return debtorIban;
    }

    public String getCreditorIban() {
        return creditorIban;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReference() {
        return reference;
    }

    public String getRemittanceInformation() {
        return remittanceInformation;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRejectionCode() {
        return rejectionCode;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
