package dev.vrailas.payments.reconciliation.adapter.in.web;

import dev.vrailas.payments.reconciliation.application.ReconciliationService;
import dev.vrailas.payments.reconciliation.application.ReconciliationService.Outcome;
import dev.vrailas.payments.reconciliation.domain.Credit;
import dev.vrailas.payments.reconciliation.domain.CreditResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
class ReconciliationController {

    private static final int MAX_XML_BYTES = 5_242_880;

    /** Result of importing one camt.054 file. */
    record ImportSummary(int credits, int duplicates, Map<CreditResult, Integer> results) {
    }

    record ReviewItem(String entryId, String source, String reference, BigDecimal amount, String currency, CreditResult result) {
    }

    private final ReconciliationService reconciliation;
    private final Camt054Parser camt054 = new Camt054Parser();

    ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    /** Imports a camt.054 file; re-uploading the same file changes nothing (entries are deduplicated). */
    @PostMapping(path = "/api/v1/camt054", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    ResponseEntity<ImportSummary> importCamt054(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_XML_BYTES + 1);
        if (body.length > MAX_XML_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        List<Credit> credits = camt054.parse(new ByteArrayInputStream(body));
        Map<CreditResult, Integer> results = new EnumMap<>(CreditResult.class);
        int duplicates = 0;
        for (Credit credit : credits) {
            Outcome outcome = reconciliation.reconcile(credit);
            if (outcome.duplicate()) {
                duplicates++;
            } else {
                results.merge(outcome.result(), 1, Integer::sum);
            }
        }
        return ResponseEntity.ok(new ImportSummary(credits.size(), duplicates, results));
    }

    /** Credits that could not be matched cleanly and need a person to look at them. */
    @GetMapping("/api/v1/reconciliation/review")
    List<ReviewItem> needingReview() {
        return reconciliation.needingReview().stream()
                .map(c -> new ReviewItem(c.getEntryId(), c.getSource(), c.getReference(), c.getAmount(), c.getCurrency(), c.getResult()))
                .toList();
    }
}
