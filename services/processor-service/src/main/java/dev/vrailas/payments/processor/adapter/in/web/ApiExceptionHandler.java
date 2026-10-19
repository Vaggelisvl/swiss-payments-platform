package dev.vrailas.payments.processor.adapter.in.web;

import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.processor.application.AccountAlreadyExistsException;
import dev.vrailas.payments.processor.application.AccountNotFoundException;
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

    @ExceptionHandler(AccountAlreadyExistsException.class)
    ProblemDetail onExists(AccountAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "Account exists", e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail onNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
