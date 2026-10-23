package dev.vrailas.payments.assistant.domain;

import java.util.Map;

/**
 * A payment exception an operator wants explained. {@code source} is the service that raised it (e.g. "processor"),
 * {@code code} its stable machine code (e.g. "INSUFFICIENT_FUNDS" or a reconciliation result).
 */
public record ExceptionCase(String source, String code, String detail, Map<String, String> attributes) {

    public static final int MAX_DETAIL = 1000;
    public static final int MAX_ATTRIBUTES = 20;

    public ExceptionCase {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        detail = detail == null ? "" : detail;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (detail.length() > MAX_DETAIL) {
            throw new IllegalArgumentException("detail is longer than " + MAX_DETAIL + " characters");
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("more than " + MAX_ATTRIBUTES + " attributes");
        }
    }
}
