package dev.vrailas.payments.ingest.adapter.in.web;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import dev.vrailas.payments.ingest.domain.PaymentInstruction;

import java.math.BigDecimal;
import java.util.Locale;

/** Translates transport models into validated domain objects; every rule violation is a {@link DomainViolationException}. */
final class PaymentMapper {

    private PaymentMapper() {
    }

    static PaymentInstruction toInstruction(PaymentRequest request) {
        PaymentRequest.Reference ref = request.reference();
        return new PaymentInstruction(
                new Iban(request.debtorIban()),
                new Iban(request.creditorIban()),
                new Money(request.amount(), currency(request.currency())),
                ref == null ? null : PaymentReference.of(ref.type(), ref.value()),
                request.remittanceInformation());
    }

    static PaymentInstruction toInstruction(Pain001Parser.Transaction tx) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(tx.amount());
        } catch (NumberFormatException e) {
            throw new DomainViolationException("amount is not a number");
        }
        return new PaymentInstruction(
                new Iban(tx.debtorIban()),
                new Iban(tx.creditorIban()),
                new Money(amount, currency(tx.currency())),
                PaymentReference.of(tx.referenceType(), tx.reference()),
                tx.remittanceInformation());
    }

    private static Currency currency(String value) {
        try {
            return Currency.valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainViolationException("currency must be CHF or EUR");
        }
    }
}
