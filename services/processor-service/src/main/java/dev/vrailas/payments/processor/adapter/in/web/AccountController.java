package dev.vrailas.payments.processor.adapter.in.web;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.processor.application.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping(path = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse open(@RequestBody AccountRequest request) {
        return AccountResponse.from(accounts.open(request.iban(), currency(request.currency()), request.balance()));
    }

    @GetMapping("/{iban}")
    AccountResponse get(@PathVariable String iban) {
        return AccountResponse.from(accounts.get(iban));
    }

    private static Currency currency(String value) {
        try {
            return Currency.valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainViolationException("currency must be CHF or EUR");
        }
    }
}
