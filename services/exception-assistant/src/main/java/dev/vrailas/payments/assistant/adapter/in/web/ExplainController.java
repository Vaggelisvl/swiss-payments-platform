package dev.vrailas.payments.assistant.adapter.in.web;

import dev.vrailas.payments.assistant.application.ExplainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only advice: the assistant explains an exception, it never changes a payment. */
@RestController
@RequestMapping("/api/v1/exceptions")
class ExplainController {

    private final ExplainService service;

    ExplainController(ExplainService service) {
        this.service = service;
    }

    @PostMapping("/explain")
    ExplainResponse explain(@Valid @RequestBody ExplainRequest request) {
        return ExplainResponse.from(service.explain(request.toDomain()));
    }
}
