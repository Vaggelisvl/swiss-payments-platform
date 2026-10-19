package dev.vrailas.payments.ingest.application;

import java.util.UUID;

public final class PaymentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(UUID id) {
        super("Payment " + id + " not found");
    }
}
