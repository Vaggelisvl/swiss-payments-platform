package dev.vrailas.payments.reconciliation.adapter.in.web;

import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.reconciliation.application.InvoiceAlreadyExistsException;
import dev.vrailas.payments.reconciliation.application.InvoiceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainViolationException.class)
    ProblemDetail onViolation(DomainViolationException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(Camt054Exception.class)
    ProblemDetail onCamt(Camt054Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid camt.054 document", e.getMessage());
    }

    @ExceptionHandler(InvoiceAlreadyExistsException.class)
    ProblemDetail onExists(InvoiceAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "Invoice exists", e.getMessage());
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    ProblemDetail onNotFound(InvoiceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Invoice not found", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
