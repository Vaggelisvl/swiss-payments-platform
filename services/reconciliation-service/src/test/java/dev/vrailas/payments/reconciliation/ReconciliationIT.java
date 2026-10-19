package dev.vrailas.payments.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vrailas.payments.contracts.PaymentBooked;
import dev.vrailas.payments.contracts.Topology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Needs PostgreSQL ("reconciliation" database) and RabbitMQ. Run with {@code mvn verify -Pit}. */
@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM credit_entry");
        jdbc.update("DELETE FROM invoice");
    }

    private String openInvoice(String reference, String amount, String currency) throws Exception {
        String body = """
                {"referenceType": "QRR", "reference": "%s", "amount": %s, "currency": "%s"}""".formatted(reference, amount, currency);
        return json.readTree(mvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode invoice(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/invoices/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void paymentBooked(UUID id, String reference, String amount, String currency) {
        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_BOOKED, new PaymentBooked(id, "CH4431999123000889012",
                new BigDecimal(amount), currency, "QRR", reference, Instant.now()));
    }

    private void awaitInvoiceStatus(String id, String status) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(invoice(id).get("status").asText()).isEqualTo(status));
    }

    @Test
    void bookedPaymentSettlesTheMatchingInvoice() throws Exception {
        String ref = TestReferences.qrr();
        String invoiceId = openInvoice(ref, "100.00", "CHF");

        paymentBooked(UUID.randomUUID(), ref, "100.00", "CHF");

        awaitInvoiceStatus(invoiceId, "PAID");
        assertThat(invoice(invoiceId).get("paidAmount").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void instalmentsAreAccumulatedAndSurplusNeedsReview() throws Exception {
        String ref = TestReferences.qrr();
        String invoiceId = openInvoice(ref, "100.00", "CHF");

        paymentBooked(UUID.randomUUID(), ref, "40.00", "CHF");
        awaitInvoiceStatus(invoiceId, "PARTIALLY_PAID");
        paymentBooked(UUID.randomUUID(), ref, "70.00", "CHF");
        awaitInvoiceStatus(invoiceId, "OVERPAID");

        mvc.perform(get("/api/v1/reconciliation/review")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.result == 'OVERPAID')]").exists());
    }

    @Test
    void unknownReferenceAndWrongCurrencyAreSentToReview() throws Exception {
        String ref = TestReferences.qrr();
        String invoiceId = openInvoice(ref, "100.00", "CHF");
        UUID wrongCurrency = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();

        paymentBooked(wrongCurrency, ref, "100.00", "EUR");
        paymentBooked(unknown, TestReferences.qrr(), "5.00", "CHF");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT result FROM credit_entry ORDER BY result", String.class))
                        .containsExactly("CURRENCY_MISMATCH", "UNMATCHED"));
        assertThat(invoice(invoiceId).get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void redeliveredEventsAreBookedOnce() throws Exception {
        String ref = TestReferences.qrr();
        String invoiceId = openInvoice(ref, "100.00", "CHF");
        UUID paymentId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            paymentBooked(paymentId, ref, "40.00", "CHF");
        }

        awaitInvoiceStatus(invoiceId, "PARTIALLY_PAID");
        await().during(2, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(invoice(invoiceId).get("paidAmount").decimalValue()).isEqualByComparingTo("40.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_entry", Integer.class)).isEqualTo(1);
    }

    @Test
    void camt054FileIsImportedOnceEvenWhenUploadedTwice() throws Exception {
        String ref1 = TestReferences.qrr();
        String ref2 = TestReferences.qrr();
        String invoiceId = openInvoice(ref1, "1949.75", "CHF");
        String xml = new String(new ClassPathResource("camt054-sample.xml").getContentAsByteArray(), StandardCharsets.UTF_8)
                .replace("${RUN}", UUID.randomUUID().toString().substring(0, 8)).replace("${AMOUNT1}", "1949.75")
                .replace("${REF1}", ref1).replace("${REF2}", ref2);

        mvc.perform(post("/api/v1/camt054").contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credits").value(2))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.results.MATCHED").value(1))
                .andExpect(jsonPath("$.results.UNMATCHED").value(1));
        mvc.perform(post("/api/v1/camt054").contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates").value(2));

        assertThat(invoice(invoiceId).get("status").asText()).isEqualTo("PAID");
        assertThat(invoice(invoiceId).get("paidAmount").decimalValue()).isEqualByComparingTo("1949.75");
    }

    @Test
    void parallelCreditsForOneInvoiceAreAllApplied() throws Exception {
        String ref = TestReferences.qrr();
        String invoiceId = openInvoice(ref, "100.00", "CHF");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String body = """
                        <Document><Ntfctn><Id>N%1$d</Id><Ntry><Amt Ccy="CHF">10.00</Amt><CdtDbtInd>CRDT</CdtDbtInd>
                        <AcctSvcrRef>PAR-%2$s-%1$d</AcctSvcrRef>
                        <NtryDtls><TxDtls><RmtInf><Strd><CdtrRefInf><Ref>%3$s</Ref></CdtrRefInf></Strd></RmtInf></TxDtls></NtryDtls>
                        </Ntry></Ntfctn></Document>""".formatted(i, invoiceId, ref);
                calls.add(() -> {
                    mvc.perform(post("/api/v1/camt054").contentType(MediaType.APPLICATION_XML).content(body)).andExpect(status().isOk());
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(calls)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(invoice(invoiceId).get("paidAmount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(invoice(invoiceId).get("status").asText()).isEqualTo("PAID");
    }

    @Test
    void invoiceValidationAndDuplicates() throws Exception {
        String ref = TestReferences.qrr();
        openInvoice(ref, "10.00", "CHF");

        mvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceType\":\"QRR\",\"reference\":\"%s\",\"amount\":10,\"currency\":\"CHF\"}".formatted(ref)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceType\":\"QRR\",\"reference\":\"123\",\"amount\":10,\"currency\":\"CHF\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/invoices").param("reference", ref)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/invoices/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/camt054").contentType(MediaType.APPLICATION_XML).content("<Document>")).andExpect(status().isBadRequest());
    }
}
