package dev.vrailas.payments.assistant.adapter.in.web;

import dev.vrailas.payments.assistant.domain.Explanation;

record ExplainResponse(String cause, String suggestedAction, String severity, String origin) {

    static ExplainResponse from(Explanation explanation) {
        return new ExplainResponse(explanation.cause(), explanation.suggestedAction(), explanation.severity().name(), explanation.origin().name());
    }
}
