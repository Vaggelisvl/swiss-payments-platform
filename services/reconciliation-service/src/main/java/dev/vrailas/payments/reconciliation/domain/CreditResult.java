package dev.vrailas.payments.reconciliation.domain;

/** How an incoming credit relates to the invoices. UNMATCHED, CURRENCY_MISMATCH and OVERPAID need manual review. */
public enum CreditResult {
    /** The credit completed the invoice exactly. */
    MATCHED,
    /** The invoice is still not fully paid. */
    PARTIAL,
    /** The invoice received more than its amount. */
    OVERPAID,
    /** No invoice carries this reference (or the credit has none). */
    UNMATCHED,
    /** The invoice exists but is denominated in another currency. */
    CURRENCY_MISMATCH;

    public boolean needsReview() {
        return this == UNMATCHED || this == CURRENCY_MISMATCH || this == OVERPAID;
    }
}
