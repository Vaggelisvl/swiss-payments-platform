package dev.vrailas.payments.processor.domain;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RulesTest {

    private static final Iban A = new Iban("CH9300762011623852957");
    private static final Iban B = new Iban("LI21088100002324013AA");

    private static PaymentOrder order(Iban debtor, Iban creditor, String amount) {
        return new PaymentOrder(UUID.randomUUID(), debtor, creditor, Money.of(amount, Currency.CHF), PaymentReference.of(null, null));
    }

    @Test
    void amountLimitRejectsOnlyAboveTheLimit() {
        PaymentRule rule = Rules.amountLimit(new BigDecimal("1000"));

        assertThat(rule.evaluate(order(A, B, "1000.00"))).isEmpty();
        assertThat(rule.evaluate(order(A, B, "1000.01"))).get().extracting(Rejection::code).isEqualTo(Rejection.AMOUNT_LIMIT_EXCEEDED);
    }

    @Test
    void denylistChecksBothSides() {
        PaymentRule rule = Rules.denylist(Set.of(B));

        assertThat(rule.evaluate(order(A, B, "1"))).get().extracting(Rejection::code).isEqualTo(Rejection.DENYLISTED_ACCOUNT);
        assertThat(rule.evaluate(order(B, A, "1"))).isPresent();
        assertThat(rule.evaluate(order(A, new Iban("DE89370400440532013000"), "1"))).isEmpty();
    }

    @Test
    void sameAccountIsRejected() {
        assertThat(Rules.differentAccounts().evaluate(order(A, A, "1"))).get().extracting(Rejection::code).isEqualTo(Rejection.SAME_ACCOUNT);
        assertThat(Rules.differentAccounts().evaluate(order(A, B, "1"))).isEmpty();
    }

    @Test
    void engineStopsAtTheFirstRejection() {
        RuleEngine engine = new RuleEngine(List.of(Rules.differentAccounts(), Rules.amountLimit(BigDecimal.ONE)));

        assertThat(engine.evaluate(order(A, A, "50"))).get().extracting(Rejection::code).isEqualTo(Rejection.SAME_ACCOUNT);
        assertThat(engine.evaluate(order(A, B, "50"))).get().extracting(Rejection::code).isEqualTo(Rejection.AMOUNT_LIMIT_EXCEEDED);
        assertThat(engine.evaluate(order(A, B, "1"))).isEmpty();
    }
}
