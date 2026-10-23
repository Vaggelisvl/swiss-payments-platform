package dev.vrailas.payments.assistant.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    void masksIbanWithAndWithoutSpaces() {
        assertThat(Redactor.redact("from CH9300762011623852957 to DE89 3704 0044 0532 0130 00"))
                .isEqualTo("from CH93…2957 to DE89…3000");
    }

    @Test
    void masksQrReference() {
        assertThat(Redactor.redact("ref 210000000003139471430009017 not found")).isEqualTo("ref [REFERENCE] not found");
    }

    @Test
    void redactsDetailAndAttributesButKeepsCodes() {
        ExceptionCase redacted = Redactor.redact(new ExceptionCase("processor", "UNKNOWN_DEBTOR", "no account CH9300762011623852957",
                Map.of("debtor", "CH9300762011623852957")));
        assertThat(redacted.code()).isEqualTo("UNKNOWN_DEBTOR");
        assertThat(redacted.detail()).doesNotContain("0076201162385");
        assertThat(redacted.attributes().get("debtor")).isEqualTo("CH93…2957");
    }
}
