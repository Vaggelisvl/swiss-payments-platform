package dev.vrailas.payments.domain;

import java.util.regex.Pattern;

/** International bank account number, validated with the ISO 7064 mod 97 check. */
public record Iban(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}\\d{2}[0-9A-Z]{11,30}");

    public Iban {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase();
        Checks.require(FORMAT.matcher(normalized).matches(), "IBAN has an invalid format");
        Checks.require(Iso7064.hasValidChecksum(normalized), "IBAN checksum is invalid");
        value = normalized;
    }

    /** QR-IBANs (Swiss institution identifier 30000-31999) must be used with a QR reference. */
    public boolean isQrIban() {
        if (!value.startsWith("CH") && !value.startsWith("LI")) {
            return false;
        }
        String institutionId = value.substring(4, 9);
        if (!institutionId.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int id = Integer.parseInt(institutionId);
        return id >= 30_000 && id <= 31_999;
    }
}
