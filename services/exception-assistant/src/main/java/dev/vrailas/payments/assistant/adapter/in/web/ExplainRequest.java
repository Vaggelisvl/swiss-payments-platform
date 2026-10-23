package dev.vrailas.payments.assistant.adapter.in.web;

import dev.vrailas.payments.assistant.domain.ExceptionCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

record ExplainRequest(
        @NotBlank @Size(max = 50) String source,
        @NotBlank @Size(max = 100) String code,
        @Size(max = ExceptionCase.MAX_DETAIL) String detail,
        @Size(max = ExceptionCase.MAX_ATTRIBUTES) Map<String, String> attributes) {

    ExceptionCase toDomain() {
        return new ExceptionCase(source, code, detail, attributes);
    }
}
