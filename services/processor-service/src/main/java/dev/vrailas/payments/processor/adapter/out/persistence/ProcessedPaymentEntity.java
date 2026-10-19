package dev.vrailas.payments.processor.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_payment")
public class ProcessedPaymentEntity {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(nullable = false)
    private String outcome;
    @Column(name = "reason_code")
    private String reasonCode;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedPaymentEntity() {
    }

    public ProcessedPaymentEntity(UUID paymentId, String outcome, String reasonCode, Instant processedAt) {
        this.paymentId = paymentId;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.processedAt = processedAt;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
