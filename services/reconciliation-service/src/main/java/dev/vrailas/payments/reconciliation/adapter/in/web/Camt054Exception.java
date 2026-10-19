package dev.vrailas.payments.reconciliation.adapter.in.web;

public final class Camt054Exception extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public Camt054Exception(String message) {
        super(message);
    }

    public Camt054Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
