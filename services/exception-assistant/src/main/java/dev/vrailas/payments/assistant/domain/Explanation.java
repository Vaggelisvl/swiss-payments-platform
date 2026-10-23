package dev.vrailas.payments.assistant.domain;

/** What happened and what to do about it. {@code origin} tells the operator whether a model or the catalogue wrote it. */
public record Explanation(String cause, String suggestedAction, Severity severity, Origin origin) {

    public enum Origin { AI, CATALOGUE }

    public Explanation withOrigin(Origin newOrigin) {
        return new Explanation(cause, suggestedAction, severity, newOrigin);
    }
}
