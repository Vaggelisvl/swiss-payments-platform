package dev.vrailas.payments.ingest.adapter.in.web;

import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.ingest.application.PaymentIntakeService;
import dev.vrailas.payments.ingest.application.PaymentIntakeService.Outcome;
import dev.vrailas.payments.ingest.application.PaymentStatusService;
import dev.vrailas.payments.ingest.domain.PaymentInstruction;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_XML_BYTES = 1_048_576;

    private final PaymentIntakeService intake;
    private final PaymentStatusService status;
    private final MeterRegistry meters;
    private final Pain001Parser pain001 = new Pain001Parser();

    PaymentController(PaymentIntakeService intake, PaymentStatusService status, MeterRegistry meters) {
        this.intake = intake;
        this.status = status;
        this.meters = meters;
    }

    /** Creates a payment. The {@code Idempotency-Key} header makes retries safe. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PaymentResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody PaymentRequest request) {
        requireValidKey(idempotencyKey);
        return respond(intake.accept(idempotencyKey, PaymentMapper.toInstruction(request)), "json");
    }

    /** Creates one payment per credit transfer of an ISO 20022 pain.001 document. */
    @PostMapping(path = "/pain001", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE}, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<PaymentResponse>> createFromPain001(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_XML_BYTES + 1);
        if (body.length > MAX_XML_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        List<Pain001Parser.Transaction> transactions = pain001.parse(new ByteArrayInputStream(body));
        // Validate everything first so that a bad transaction does not leave a half-accepted file.
        List<PaymentInstruction> instructions = transactions.stream().map(PaymentMapper::toInstruction).toList();
        List<PaymentResponse> responses = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++) {
            Outcome outcome = intake.accept(transactions.get(i).idempotencyKey(), instructions.get(i));
            count(outcome, "pain001");
            responses.add(PaymentResponse.from(outcome.payment()));
        }
        return ResponseEntity.accepted().body(responses);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(status.get(id));
    }

    private ResponseEntity<PaymentResponse> respond(Outcome outcome, String channel) {
        count(outcome, channel);
        PaymentResponse body = PaymentResponse.from(outcome.payment());
        URI location = URI.create("/api/v1/payments/" + body.paymentId());
        if (outcome.replayed()) {
            return ResponseEntity.ok().header(HttpHeaders.LOCATION, location.toString()).header("Idempotent-Replayed", "true").body(body);
        }
        return ResponseEntity.accepted().location(location).body(body);
    }

    private void count(Outcome outcome, String channel) {
        meters.counter(outcome.replayed() ? "payments.duplicates" : "payments.received", "channel", channel).increment();
    }

    private static void requireValidKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new DomainViolationException("Idempotency-Key must be 1 to " + MAX_KEY_LENGTH + " characters");
        }
    }
}
