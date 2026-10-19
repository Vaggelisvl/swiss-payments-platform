package dev.vrailas.payments.domain;

import java.util.regex.Pattern;

/** ISO 11649 creditor reference (RF + 2 check digits + up to 21 alphanumerics). */
public record CreditorReference(String value) implements PaymentReference {

    private static final Pattern FORMAT = Pattern.compile("RF\\d{2}[0-9A-Z]{1,21}");

    public CreditorReference {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase();
        Checks.require(FORMAT.matcher(normalized).matches(), "creditor reference must match RF + 2 digits + up to 21 alphanumerics");
        Checks.require(Iso7064.hasValidChecksum(normalized), "creditor reference checksum is invalid");
        value = normalized;
    }

    @Override
    public String type() {
        return "SCOR";
    }
}
