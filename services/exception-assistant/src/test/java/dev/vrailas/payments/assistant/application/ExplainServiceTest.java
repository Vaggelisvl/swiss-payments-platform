package dev.vrailas.payments.assistant.application;

import dev.vrailas.payments.assistant.domain.ExceptionCase;
import dev.vrailas.payments.assistant.domain.Explanation;
import dev.vrailas.payments.assistant.domain.Explanation.Origin;
import dev.vrailas.payments.assistant.domain.ExplanationUnavailableException;
import dev.vrailas.payments.assistant.domain.Severity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExplainServiceTest {

    private final ExceptionCase input = new ExceptionCase("processor", "UNKNOWN_DEBTOR", "no account CH9300762011623852957", Map.of());

    @Test
    void modelAnswerIsMarkedAiAndInputIsRedactedBeforeTheModelSeesIt() {
        AtomicReference<ExceptionCase> seen = new AtomicReference<>();
        ExplainService service = new ExplainService((c, hint) -> {
            seen.set(c);
            return new Explanation("because", "do this", Severity.LOW, Origin.CATALOGUE);
        }, new SimpleMeterRegistry());

        Explanation result = service.explain(input);

        assertThat(result.origin()).isEqualTo(Origin.AI);
        assertThat(seen.get().detail()).isEqualTo("no account CH93…2957");
    }

    @Test
    void fallsBackToCatalogueWhenModelIsUnavailable() {
        ExplainService service = new ExplainService((c, hint) -> {
            throw new ExplanationUnavailableException("down");
        }, new SimpleMeterRegistry());

        Explanation result = service.explain(input);

        assertThat(result.origin()).isEqualTo(Origin.CATALOGUE);
        assertThat(result.cause()).contains("not known to the ledger");
    }
}
