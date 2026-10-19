package dev.vrailas.payments.domain;

/** A business rule of the payment domain was violated (invalid IBAN, reference, amount...). */
public final class DomainViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DomainViolationException(String message) {
        super(message);
    }
}
