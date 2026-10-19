package dev.vrailas.payments.processor.domain;

/** Why a payment was refused. {@code code} is stable and machine readable, {@code reason} is for humans. */
public record Rejection(String code, String reason) {

    public static final String AMOUNT_LIMIT_EXCEEDED = "AMOUNT_LIMIT_EXCEEDED";
    public static final String DENYLISTED_ACCOUNT = "DENYLISTED_ACCOUNT";
    public static final String SAME_ACCOUNT = "SAME_ACCOUNT";
    public static final String UNKNOWN_DEBTOR = "UNKNOWN_DEBTOR";
    public static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
}
