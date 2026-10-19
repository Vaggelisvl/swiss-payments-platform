package dev.vrailas.payments.processor.application;

public final class AccountNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String iban) {
        super("Account " + iban + " not found");
    }
}
