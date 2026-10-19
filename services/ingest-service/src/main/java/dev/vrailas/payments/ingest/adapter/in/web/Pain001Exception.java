package dev.vrailas.payments.ingest.adapter.in.web;

/** The uploaded pain.001 document is malformed or lacks mandatory elements. */
public final class Pain001Exception extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Pain001Exception(String message) {
        super(message);
    }

    public Pain001Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
