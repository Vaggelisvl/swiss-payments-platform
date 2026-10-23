package dev.vrailas.payments.assistant.domain;

/** The model could not produce a usable answer (no key, timeout, error status, malformed output). */
public class ExplanationUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    public ExplanationUnavailableException(String message) {
        super(message);
    }

    public ExplanationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
