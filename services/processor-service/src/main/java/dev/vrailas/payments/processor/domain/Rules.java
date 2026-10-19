package dev.vrailas.payments.processor.domain;

import dev.vrailas.payments.domain.Iban;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** The built-in rules. */
public final class Rules {

    private Rules() {
    }

    public static PaymentRule amountLimit(BigDecimal max) {
        return order -> order.money().amount().compareTo(max) > 0
                ? Optional.of(new Rejection(Rejection.AMOUNT_LIMIT_EXCEEDED, "Amount exceeds the limit of " + max.toPlainString()))
                : Optional.empty();
    }

    public static PaymentRule denylist(Set<Iban> blocked) {
        return order -> blocked.contains(order.debtor()) || blocked.contains(order.creditor())
                ? Optional.of(new Rejection(Rejection.DENYLISTED_ACCOUNT, "An account of this payment is on the deny list"))
                : Optional.empty();
    }

    public static PaymentRule differentAccounts() {
        return order -> order.debtor().equals(order.creditor())
                ? Optional.of(new Rejection(Rejection.SAME_ACCOUNT, "Debtor and creditor account must differ"))
                : Optional.empty();
    }
}
