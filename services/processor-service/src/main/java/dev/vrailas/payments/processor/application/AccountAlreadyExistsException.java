package dev.vrailas.payments.processor.application;

public final class AccountAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountAlreadyExistsException(String iban) {
        super("Account " + iban + " already exists");
    }
}
