package dev.vrailas.payments.reconciliation.application;

public final class InvoiceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvoiceNotFoundException(String key) {
        super("Invoice " + key + " not found");
    }
}
