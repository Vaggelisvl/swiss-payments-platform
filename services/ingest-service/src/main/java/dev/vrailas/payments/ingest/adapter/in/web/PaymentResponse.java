package dev.vrailas.payments.ingest.adapter.in.web;

import dev.vrailas.payments.ingest.adapter.out.persistence.PaymentEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String status,
        String debtorIban,
        String creditorIban,
        BigDecimal amount,
        String currency,
        String rejectionCode,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(PaymentEntity p) {
        return new PaymentResponse(p.getId(), p.getStatus().name(), p.getDebtorIban(), p.getCreditorIban(), p.getAmount(),
                p.getCurrency(), p.getRejectionCode(), p.getRejectionReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
