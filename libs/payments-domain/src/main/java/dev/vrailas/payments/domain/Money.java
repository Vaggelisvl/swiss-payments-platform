package dev.vrailas.payments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A strictly positive amount of money with at most two decimals. */
public record Money(BigDecimal amount, Currency currency) {

    private static final BigDecimal MAX = new BigDecimal("999999999.99");

    public Money {
        Checks.require(amount != null && currency != null, "amount and currency are required");
        Checks.require(amount.stripTrailingZeros().scale() <= 2, "amount must not have more than two decimals");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        Checks.require(amount.signum() > 0 && amount.compareTo(MAX) <= 0, "amount must be between 0.01 and 999999999.99");
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }
}
