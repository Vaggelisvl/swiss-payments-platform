package dev.vrailas.payments.ingest.application;

/** The idempotency key was already used with a different payload. */
public final class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String key) {
        super("Idempotency key '" + key + "' was already used with a different request");
    }
}
