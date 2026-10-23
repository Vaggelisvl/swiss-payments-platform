package dev.vrailas.payments.assistant.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Masks account numbers and payment references before anything leaves the platform for a third-party model. */
public final class Redactor {

    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30}\\b");
    private static final Pattern REFERENCE = Pattern.compile("\\b\\d{26,27}\\b");

    private Redactor() {
    }

    public static String redact(String text) {
        String masked = IBAN.matcher(text).replaceAll(m -> maskIban(m.group()));
        return REFERENCE.matcher(masked).replaceAll("[REFERENCE]");
    }

    public static ExceptionCase redact(ExceptionCase exceptionCase) {
        Map<String, String> attributes = new LinkedHashMap<>();
        exceptionCase.attributes().forEach((key, value) -> attributes.put(redact(key), redact(value)));
        return new ExceptionCase(exceptionCase.source(), exceptionCase.code(), redact(exceptionCase.detail()), attributes);
    }

    private static String maskIban(String iban) {
        String compact = iban.replace(" ", "");
        return compact.substring(0, 4) + "…" + compact.substring(compact.length() - 4);
    }
}
