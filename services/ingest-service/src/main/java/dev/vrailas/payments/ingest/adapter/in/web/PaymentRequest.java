package dev.vrailas.payments.ingest.adapter.in.web;

import java.math.BigDecimal;

/** JSON representation of a payment order. Business validation happens in the domain value objects. */
public record PaymentRequest(
        String debtorIban,
        String creditorIban,
        BigDecimal amount,
        String currency,
        Reference reference,
        String remittanceInformation) {

    /** {@code type} is QRR, SCOR or NON. */
    public record Reference(String type, String value) {
    }
}
