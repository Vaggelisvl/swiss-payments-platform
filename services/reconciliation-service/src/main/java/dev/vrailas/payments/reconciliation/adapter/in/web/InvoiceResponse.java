package dev.vrailas.payments.reconciliation.adapter.in.web;

import dev.vrailas.payments.reconciliation.adapter.out.persistence.InvoiceEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceResponse(UUID id, String referenceType, String reference, BigDecimal amount, String currency,
                              BigDecimal paidAmount, String status) {

    static InvoiceResponse from(InvoiceEntity i) {
        return new InvoiceResponse(i.getId(), i.getReferenceType(), i.getReference(), i.getAmount(), i.getCurrency(),
                i.getPaidAmount(), i.getStatus().name());
    }
}
