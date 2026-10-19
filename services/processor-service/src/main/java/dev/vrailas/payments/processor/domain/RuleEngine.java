package dev.vrailas.payments.processor.domain;

import java.util.List;
import java.util.Optional;

/** Evaluates rules in order and stops at the first rejection. */
public final class RuleEngine {

    private final List<PaymentRule> rules;

    public RuleEngine(List<PaymentRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Optional<Rejection> evaluate(PaymentOrder order) {
        return rules.stream().map(rule -> rule.evaluate(order)).flatMap(Optional::stream).findFirst();
    }
}
