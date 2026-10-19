package dev.vrailas.payments.ingest.domain;

import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import dev.vrailas.payments.domain.QrReference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A syntactically valid payment order. Business rules (limits, funds, deny lists) are checked later by
 * the processor; here only the shape of the data is guaranteed.
 */
public record PaymentInstruction(
        Iban debtor,
        Iban creditor,
        Money money,
        PaymentReference reference,
        String remittanceInformation) {

    private static final int MAX_REMITTANCE = 140;

    public PaymentInstruction {
        require(debtor != null && creditor != null && money != null, "debtor, creditor and amount are required");
        reference = reference == null ? PaymentReference.of(null, null) : reference;
        remittanceInformation = remittanceInformation == null ? "" : remittanceInformation.strip();
        require(remittanceInformation.length() <= MAX_REMITTANCE, "remittance information must not exceed 140 characters");
        require(creditor.isQrIban() == reference instanceof QrReference,
                creditor.isQrIban() ? "a QR-IBAN creditor requires a QR reference" : "a QR reference requires a QR-IBAN creditor");
    }

    /** Stable hash of the content; the same idempotency key with different content is a client error. */
    public String fingerprint() {
        String canonical = String.join("|", debtor.value(), creditor.value(), money.amount().toPlainString(),
                money.currency().name(), reference.type(), reference.value(), remittanceInformation);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainViolationException(message);
        }
    }
}
