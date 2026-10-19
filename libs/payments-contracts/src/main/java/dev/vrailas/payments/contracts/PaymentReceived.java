package dev.vrailas.payments.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published by the ingest service once a payment was accepted. */
public record PaymentReceived(
        UUID paymentId,
        String debtorIban,
        String creditorIban,
        BigDecimal amount,
        String currency,
        String referenceType,
        String reference,
        String remittanceInformation,
        Instant receivedAt) {
}
