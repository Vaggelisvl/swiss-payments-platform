package dev.vrailas.payments.processor.adapter.in.web;

import dev.vrailas.payments.processor.adapter.out.persistence.AccountEntity;

import java.math.BigDecimal;

public record AccountResponse(String iban, String currency, BigDecimal balance) {

    static AccountResponse from(AccountEntity a) {
        return new AccountResponse(a.getIban(), a.getCurrency(), a.getBalance());
    }
}
