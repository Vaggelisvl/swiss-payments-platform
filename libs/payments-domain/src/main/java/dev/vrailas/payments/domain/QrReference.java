package dev.vrailas.payments.domain;

import java.util.regex.Pattern;

/** 27-digit Swiss QR reference with a recursive modulo 10 check digit. */
public record QrReference(String value) implements PaymentReference {

    private static final Pattern FORMAT = Pattern.compile("\\d{27}");
    private static final int[] MOD10 = {0, 9, 4, 6, 8, 2, 7, 1, 3, 5};

    public QrReference {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "");
        Checks.require(FORMAT.matcher(normalized).matches(), "QR reference must consist of 27 digits");
        Checks.require(checkDigit(normalized.substring(0, 26)) == normalized.charAt(26) - '0', "QR reference check digit is invalid");
        value = normalized;
    }

    static int checkDigit(String digits) {
        int carry = 0;
        for (int i = 0; i < digits.length(); i++) {
            carry = MOD10[(carry + digits.charAt(i) - '0') % 10];
        }
        return (10 - carry) % 10;
    }

    @Override
    public String type() {
        return "QRR";
    }
}
