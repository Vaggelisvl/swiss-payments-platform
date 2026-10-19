package dev.vrailas.payments.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vrailas.payments.contracts.PaymentBooked;
import dev.vrailas.payments.contracts.PaymentReceived;
import dev.vrailas.payments.contracts.PaymentRejected;
import dev.vrailas.payments.contracts.Topology;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Needs PostgreSQL ("ingest" database) and RabbitMQ: see docker-compose.yml. Run with {@code mvn verify -Pit}. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIT {

    private static final String TEST_QUEUE = "it.ingest.received";
    private static final String BODY = """
            {"debtorIban": "CH93 0076 2011 6238 5295 7", "creditorIban": "CH44 3199 9123 0008 8901 2",
             "amount": 1949.75, "currency": "CHF",
             "reference": {"type": "QRR", "value": "210000000003139471430009017"},
             "remittanceInformation": "Invoice 10978"}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired ObjectMapper json;
    @Autowired MeterRegistry meters;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payment");
        jdbc.update("DELETE FROM outbox");
        admin.deleteQueue(TEST_QUEUE);
        Queue queue = new Queue(TEST_QUEUE, true, false, false);
        admin.declareQueue(queue);
        admin.purgeQueue(TEST_QUEUE, false);
        Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(Topology.EXCHANGE, true, false)).with(Topology.PAYMENT_RECEIVED);
        admin.declareBinding(binding);
    }

    private MvcResult submit(String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/payments").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    @Test
    void acceptedPaymentIsStoredAndPublishedExactlyOnce() throws Exception {
        MvcResult result = submit("key-1", BODY);
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        JsonNode created = json.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("paymentId").asText());
        assertThat(created.get("status").asText()).isEqualTo("RECEIVED");

        PaymentReceived event = await().atMost(10, TimeUnit.SECONDS).until(() -> rabbit.receiveAndConvert(TEST_QUEUE, 500,
                new org.springframework.core.ParameterizedTypeReference<PaymentReceived>() { }), e -> e != null);
        assertThat(event.paymentId()).isEqualTo(id);
        assertThat(event.amount()).isEqualByComparingTo("1949.75");
        assertThat(event.creditorIban()).isEqualTo("CH4431999123000889012");
        assertThat(event.reference()).isEqualTo("210000000003139471430009017");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Integer.class)).isEqualTo(1));
        assertThat(rabbit.receiveAndConvert(TEST_QUEUE, 700)).as("no duplicate publication").isNull();
    }

    @Test
    void sameKeyAndSamePayloadReturnsOriginalPayment() throws Exception {
        String first = submit("key-2", BODY).getResponse().getContentAsString();

        MvcResult replay = submit("key-2", BODY);

        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json.readTree(replay.getResponse().getContentAsString()).get("paymentId"))
                .isEqualTo(json.readTree(first).get("paymentId"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsAConflict() throws Exception {
        submit("key-3", BODY);

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-3").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("1949.75", "1949.76")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency key reused"));
    }

    @Test
    void parallelRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> submit("key-race", BODY).getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(200, 202);
            assertThat(statuses.stream().filter(s -> s == 202)).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidPaymentIsRejectedWithProblemDetailsAndNothingIsStored() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "key-4").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("210000000003139471430009017", "210000000003139471430009018")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("QR reference check digit is invalid"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Integer.class)).isZero();
    }

    @Test
    void idempotencyKeyIsMandatory() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pain001CreatesOnePaymentPerTransactionAndIsIdempotent() throws Exception {
        byte[] xml = new ClassPathResource("pain001-sample.xml").getContentAsByteArray();

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/payments/pain001").contentType(MediaType.APPLICATION_XML).content(xml))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.length()").value(2));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidTransactionInPain001StoresNothing() throws Exception {
        String xml = new String(new ClassPathResource("pain001-sample.xml").getContentAsByteArray())
                .replace("RF18539007547034", "RF19539007547034");

        mvc.perform(post("/api/v1/payments/pain001").contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Integer.class)).isZero();
    }

    @Test
    void outcomeEventsUpdateThePaymentStatus() throws Exception {
        UUID booked = UUID.fromString(json.readTree(submit("key-5", BODY).getResponse().getContentAsString()).get("paymentId").asText());
        UUID rejected = UUID.fromString(json.readTree(submit("key-6", BODY.replace("1949.75", "5.00")).getResponse().getContentAsString()).get("paymentId").asText());

        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_BOOKED, new PaymentBooked(booked, "CH4431999123000889012",
                new BigDecimal("1949.75"), "CHF", "QRR", "210000000003139471430009017", Instant.now()));
        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_REJECTED, new PaymentRejected(rejected, "INSUFFICIENT_FUNDS", "Not enough funds", Instant.now()));
        // a later contradictory event must not overwrite a final state
        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_REJECTED, new PaymentRejected(booked, "LATE", "late", Instant.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            mvc.perform(get("/api/v1/payments/" + booked)).andExpect(jsonPath("$.status").value("BOOKED"));
            mvc.perform(get("/api/v1/payments/" + rejected)).andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionCode").value("INSUFFICIENT_FUNDS"));
        });
        mvc.perform(get("/api/v1/payments/" + UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(meters.find("payments.received").tag("channel", "json").counter()).isNotNull();
    }
}
