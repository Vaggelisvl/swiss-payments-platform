package dev.vrailas.payments.reconciliation.adapter.in.web;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.reconciliation.application.InvoiceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
class InvoiceController {

    private final InvoiceService invoices;

    InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceResponse open(@RequestBody InvoiceRequest request) {
        return InvoiceResponse.from(invoices.open(request.referenceType(), request.reference(), request.amount(), currency(request.currency())));
    }

    @GetMapping("/{id}")
    InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.from(invoices.get(id));
    }

    @GetMapping
    InvoiceResponse getByReference(@RequestParam String reference) {
        return InvoiceResponse.from(invoices.getByReference(reference));
    }

    private static Currency currency(String value) {
        try {
            return Currency.valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainViolationException("currency must be CHF or EUR");
        }
    }
}
