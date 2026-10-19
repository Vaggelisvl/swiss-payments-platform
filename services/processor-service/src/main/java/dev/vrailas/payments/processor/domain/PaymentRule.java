package dev.vrailas.payments.processor.domain;

import java.util.Optional;

/** One business rule. Rules are pure and independent so that they can be tested and combined freely. */
@FunctionalInterface
public interface PaymentRule {

    Optional<Rejection> evaluate(PaymentOrder order);
}
