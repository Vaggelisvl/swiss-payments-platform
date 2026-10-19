package dev.vrailas.payments.reconciliation.application;

public final class InvoiceAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvoiceAlreadyExistsException(String reference) {
        super("An invoice with reference " + reference + " already exists");
    }
}
