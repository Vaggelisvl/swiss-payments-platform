package dev.vrailas.payments.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An incoming credit from any source. {@code entryId} is unique per real-world credit and scoped by source
 * ("event:&lt;paymentId&gt;", "camt:&lt;AcctSvcrRef&gt;"), which makes booking idempotent.
 */
public record Credit(String entryId, Source source, String reference, BigDecimal amount, String currency, LocalDate bookedOn) {

    public enum Source {
        EVENT,
        CAMT054
    }
}
