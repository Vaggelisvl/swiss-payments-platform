package dev.vrailas.payments.reconciliation.adapter.in.web;

import java.math.BigDecimal;

public record InvoiceRequest(String referenceType, String reference, BigDecimal amount, String currency) {
}
