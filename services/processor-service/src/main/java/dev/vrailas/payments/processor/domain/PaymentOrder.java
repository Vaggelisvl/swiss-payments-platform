package dev.vrailas.payments.processor.domain;

import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;

import java.util.UUID;

/** A payment to be processed, expressed with validated domain value objects. */
public record PaymentOrder(UUID id, Iban debtor, Iban creditor, Money money, PaymentReference reference) {
}
