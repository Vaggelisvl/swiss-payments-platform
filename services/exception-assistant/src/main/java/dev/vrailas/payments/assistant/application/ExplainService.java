package dev.vrailas.payments.assistant.application;

import dev.vrailas.payments.assistant.domain.Catalogue;
import dev.vrailas.payments.assistant.domain.ExceptionCase;
import dev.vrailas.payments.assistant.domain.Explanation;
import dev.vrailas.payments.assistant.domain.Explanation.Origin;
import dev.vrailas.payments.assistant.domain.ExplanationModel;
import dev.vrailas.payments.assistant.domain.ExplanationUnavailableException;
import dev.vrailas.payments.assistant.domain.Redactor;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Explains an exception with the model when possible, and with the catalogue otherwise. The model never sees unredacted data. */
@Service
public class ExplainService {

    private static final Logger log = LoggerFactory.getLogger(ExplainService.class);

    private final ExplanationModel model;
    private final MeterRegistry meters;

    public ExplainService(ExplanationModel model, MeterRegistry meters) {
        this.model = model;
        this.meters = meters;
    }

    public Explanation explain(ExceptionCase exceptionCase) {
        ExceptionCase redacted = Redactor.redact(exceptionCase);
        Explanation hint = Catalogue.lookup(redacted);
        try {
            Explanation answer = model.explain(redacted, hint).withOrigin(Origin.AI);
            meters.counter("assistant.explanations", "origin", "ai").increment();
            return answer;
        } catch (ExplanationUnavailableException e) {
            log.warn("Model unavailable for {}/{}, using catalogue: {}", redacted.source(), redacted.code(), e.getMessage());
            meters.counter("assistant.explanations", "origin", "catalogue").increment();
            return hint;
        }
    }
}
