package dev.vrailas.payments.domain;

import java.util.Locale;

/** Structured creditor reference of a payment. Sealed: consumers handle every variant exhaustively. */
public sealed interface PaymentReference permits QrReference, CreditorReference, NoReference {

    /** QRR, SCOR or NON. */
    String type();

    /** Empty for {@link NoReference}. */
    String value();

    static PaymentReference of(String type, String value) {
        if (type == null || type.isBlank()) {
            return new NoReference();
        }
        return switch (type.strip().toUpperCase(Locale.ROOT)) {
            case "QRR" -> new QrReference(value);
            case "SCOR" -> new CreditorReference(value);
            case "NON" -> new NoReference();
            default -> throw new DomainViolationException("reference type must be QRR, SCOR or NON");
        };
    }
}
