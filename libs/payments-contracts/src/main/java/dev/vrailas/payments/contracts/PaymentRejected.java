package dev.vrailas.payments.contracts;

import java.time.Instant;
import java.util.UUID;

/** Published by the processor when a payment violates a rule; {@code reasonCode} is stable and machine readable. */
public record PaymentRejected(UUID paymentId, String reasonCode, String reason, Instant rejectedAt) {
}
