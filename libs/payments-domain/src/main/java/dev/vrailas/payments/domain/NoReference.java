package dev.vrailas.payments.domain;

public record NoReference() implements PaymentReference {

    @Override
    public String type() {
        return "NON";
    }

    @Override
    public String value() {
        return "";
    }
}
