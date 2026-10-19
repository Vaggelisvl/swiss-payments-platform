package dev.vrailas.payments.ingest.adapter.in.web;

import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.ingest.application.IdempotencyConflictException;
import dev.vrailas.payments.ingest.application.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps failures to RFC 9457 problem details with stable titles. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainViolationException.class)
    ProblemDetail onViolation(DomainViolationException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid payment", e.getMessage());
    }

    @ExceptionHandler(Pain001Exception.class)
    ProblemDetail onPain001(Pain001Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid pain.001 document", e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail onConflict(IdempotencyConflictException e) {
        return problem(HttpStatus.CONFLICT, "Idempotency key reused", e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail onNotFound(PaymentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
