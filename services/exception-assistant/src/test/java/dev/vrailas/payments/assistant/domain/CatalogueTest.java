package dev.vrailas.payments.assistant.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueTest {

    @Test
    void knowsEveryProcessorRejection() {
        for (String code : new String[] {"AMOUNT_LIMIT_EXCEEDED", "DENYLISTED_ACCOUNT", "SAME_ACCOUNT", "UNKNOWN_DEBTOR", "CURRENCY_MISMATCH", "INSUFFICIENT_FUNDS"}) {
            assertThat(Catalogue.lookup(new ExceptionCase("processor", code, null, Map.of())).cause())
                    .as(code).doesNotContain("not in the catalogue");
        }
    }

    @Test
    void sourceDisambiguatesCurrencyMismatch() {
        assertThat(Catalogue.lookup(new ExceptionCase("reconciliation", "CURRENCY_MISMATCH", null, null)).cause()).contains("invoice");
        assertThat(Catalogue.lookup(new ExceptionCase("processor", "CURRENCY_MISMATCH", null, null)).cause()).contains("account currency");
    }

    @Test
    void denylistIsHighSeverityAndUnknownCodeFallsBack() {
        assertThat(Catalogue.lookup(new ExceptionCase("processor", "denylisted_account", null, null)).severity()).isEqualTo(Severity.HIGH);
        assertThat(Catalogue.lookup(new ExceptionCase("x", "SOMETHING_NEW", null, null)).cause()).contains("not in the catalogue");
    }
}
