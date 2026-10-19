package dev.vrailas.payments.processor.adapter.in.web;

import java.math.BigDecimal;

public record AccountRequest(String iban, String currency, BigDecimal balance) {
}
