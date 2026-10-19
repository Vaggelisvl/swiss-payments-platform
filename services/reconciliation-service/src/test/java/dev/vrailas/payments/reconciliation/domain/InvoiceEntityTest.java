package dev.vrailas.payments.reconciliation.domain;

import dev.vrailas.payments.reconciliation.adapter.out.persistence.InvoiceEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceEntityTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private static InvoiceEntity invoice(String amount) {
        return new InvoiceEntity(UUID.randomUUID(), "QRR", "210000000003139471430009017", new BigDecimal(amount), "CHF", NOW);
    }

    @Test
    void exactPaymentCompletesTheInvoice() {
        InvoiceEntity invoice = invoice("100.00");

        assertThat(invoice.receive(new BigDecimal("100.00"), "CHF", NOW)).isEqualTo(CreditResult.MATCHED);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void instalmentsAccumulateUntilPaid() {
        InvoiceEntity invoice = invoice("100.00");

        assertThat(invoice.receive(new BigDecimal("40.00"), "CHF", NOW)).isEqualTo(CreditResult.PARTIAL);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.receive(new BigDecimal("60.00"), "CHF", NOW)).isEqualTo(CreditResult.MATCHED);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void surplusIsFlaggedAsOverpaid() {
        InvoiceEntity invoice = invoice("100.00");

        assertThat(invoice.receive(new BigDecimal("100.01"), "CHF", NOW)).isEqualTo(CreditResult.OVERPAID);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERPAID);
        assertThat(CreditResult.OVERPAID.needsReview()).isTrue();
        assertThat(CreditResult.MATCHED.needsReview()).isFalse();
    }

    @Test
    void foreignCurrencyDoesNotTouchTheInvoice() {
        InvoiceEntity invoice = invoice("100.00");

        assertThat(invoice.receive(new BigDecimal("100.00"), "EUR", NOW)).isEqualTo(CreditResult.CURRENCY_MISMATCH);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0");
    }
}
