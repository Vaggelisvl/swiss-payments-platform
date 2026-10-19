package dev.vrailas.payments.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published by the processor once the ledger booking succeeded. */
public record PaymentBooked(
        UUID paymentId,
        String creditorIban,
        BigDecimal amount,
        String currency,
        String referenceType,
        String reference,
        Instant bookedAt) {
}
