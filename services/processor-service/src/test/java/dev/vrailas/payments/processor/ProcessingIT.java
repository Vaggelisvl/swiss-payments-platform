package dev.vrailas.payments.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vrailas.payments.contracts.PaymentReceived;
import dev.vrailas.payments.contracts.Topology;
import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import dev.vrailas.payments.processor.application.AccountService;
import dev.vrailas.payments.processor.application.PaymentProcessor;
import dev.vrailas.payments.processor.domain.PaymentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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

/** Needs PostgreSQL ("processor" database) and RabbitMQ. Run with {@code mvn verify -Pit}. */
@SpringBootTest(properties = "processor.denylist=LI21088100002324013AA")
class ProcessingIT {

    private static final String OUTCOMES = "it.processor.outcomes";
    private static final String DENYLISTED = "LI21088100002324013AA";

    @Autowired AccountService accounts;
    @Autowired PaymentProcessor processor;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM processed_payment");
        jdbc.update("DELETE FROM outbox");
        admin.deleteQueue(OUTCOMES);
        Queue queue = new Queue(OUTCOMES, true, false, false);
        admin.declareQueue(queue);
        TopicExchange exchange = new TopicExchange(Topology.EXCHANGE, true, false);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(Topology.PAYMENT_BOOKED));
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(Topology.PAYMENT_REJECTED));
        admin.purgeQueue("processor.payment-received.dlq", false);
    }

    private static PaymentReceived received(UUID id, String debtor, String creditor, String amount, String currency) {
        return new PaymentReceived(id, debtor, creditor, new BigDecimal(amount), currency, "NON", "", "test", Instant.now());
    }

    private void open(String iban, String balance) {
        accounts.open(iban, Currency.CHF, new BigDecimal(balance));
    }

    private JsonNode awaitOutcome(UUID paymentId) {
        List<JsonNode> seen = new ArrayList<>();
        await().atMost(15, TimeUnit.SECONDS).until(() -> {
            Message m = rabbit.receive(OUTCOMES, 300);
            if (m != null) {
                JsonNode node = json.readTree(m.getBody());
                node = ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("type", m.getMessageProperties().getReceivedRoutingKey());
                seen.add(node);
            }
            return seen.stream().anyMatch(n -> n.get("paymentId").asText().equals(paymentId.toString()));
        });
        return seen.stream().filter(n -> n.get("paymentId").asText().equals(paymentId.toString())).findFirst().orElseThrow();
    }

    private BigDecimal balance(String iban) {
        return accounts.get(iban).getBalance();
    }

    @Test
    void bookedPaymentMovesMoneyBetweenInternalAccountsAndPublishesBooked() {
        String debtor = TestIbans.next();
        String creditor = TestIbans.next();
        open(debtor, "100.00");
        open(creditor, "5.00");
        UUID id = UUID.randomUUID();

        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_RECEIVED, received(id, debtor, creditor, "30.50", "CHF"));

        JsonNode outcome = awaitOutcome(id);
        assertThat(outcome.get("type").asText()).isEqualTo(Topology.PAYMENT_BOOKED);
        assertThat(balance(debtor)).isEqualByComparingTo("69.50");
        assertThat(balance(creditor)).isEqualByComparingTo("35.50");
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM ledger_entry WHERE payment_id = ?", BigDecimal.class, id))
                .as("double entry: debit and credit cancel out").isEqualByComparingTo("0");
    }

    @Test
    void creditorOutsideTheLedgerIsSettledExternally() {
        String debtor = TestIbans.next();
        open(debtor, "50.00");
        UUID id = UUID.randomUUID();

        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_RECEIVED, received(id, debtor, "DE89370400440532013000", "20.00", "CHF"));

        assertThat(awaitOutcome(id).get("type").asText()).isEqualTo(Topology.PAYMENT_BOOKED);
        assertThat(balance(debtor)).isEqualByComparingTo("30.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entry WHERE payment_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rejectionsCarryStableReasonCodesAndLeaveBalancesUntouched() {
        String debtor = TestIbans.next();
        open(debtor, "10.00");
        String creditor = TestIbans.next();

        assertRejected(received(UUID.randomUUID(), debtor, creditor, "10.01", "CHF"), "INSUFFICIENT_FUNDS");
        assertRejected(received(UUID.randomUUID(), TestIbans.next(), creditor, "1.00", "CHF"), "UNKNOWN_DEBTOR");
        assertRejected(received(UUID.randomUUID(), debtor, creditor, "1.00", "EUR"), "CURRENCY_MISMATCH");
        assertRejected(received(UUID.randomUUID(), debtor, debtor, "1.00", "CHF"), "SAME_ACCOUNT");
        assertRejected(received(UUID.randomUUID(), debtor, creditor, "100000.01", "CHF"), "AMOUNT_LIMIT_EXCEEDED");
        assertRejected(received(UUID.randomUUID(), debtor, DENYLISTED, "1.00", "CHF"), "DENYLISTED_ACCOUNT");

        assertThat(balance(debtor)).isEqualByComparingTo("10.00");
    }

    private void assertRejected(PaymentReceived event, String code) {
        rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_RECEIVED, event);
        JsonNode outcome = awaitOutcome(event.paymentId());
        assertThat(outcome.get("type").asText()).isEqualTo(Topology.PAYMENT_REJECTED);
        assertThat(outcome.get("reasonCode").asText()).isEqualTo(code);
    }

    @Test
    void redeliveredMessagesAreProcessedOnlyOnce() {
        String debtor = TestIbans.next();
        open(debtor, "100.00");
        UUID id = UUID.randomUUID();
        PaymentReceived event = received(id, debtor, "DE89370400440532013000", "10.00", "CHF");

        for (int i = 0; i < 5; i++) {
            rabbit.convertAndSend(Topology.EXCHANGE, Topology.PAYMENT_RECEIVED, event);
        }

        awaitOutcome(id);
        await().during(2, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_payment WHERE payment_id = ?", Integer.class, id)).isEqualTo(1));
        assertThat(balance(debtor)).isEqualByComparingTo("90.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_id = ?", Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void concurrentPaymentsCanNeverOverspendAnAccount() throws Exception {
        String debtor = TestIbans.next();
        open(debtor, "100.00");
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                PaymentOrder order = new PaymentOrder(UUID.randomUUID(), new Iban(debtor), new Iban("DE89370400440532013000"),
                        Money.of("10.00", Currency.CHF), PaymentReference.of(null, null));
                calls.add(() -> {
                    processor.process(order);
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(calls)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(balance(debtor)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_payment WHERE outcome = 'BOOKED'", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_payment WHERE outcome = 'REJECTED'", Integer.class)).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM ledger_entry WHERE iban = ?", BigDecimal.class, debtor)).isEqualByComparingTo("-100.00");
    }

    @Test
    void poisonMessagesEndUpInTheDeadLetterQueue() {
        String garbage = "{\"paymentId\":\"" + UUID.randomUUID() + "\",\"debtorIban\":\"NOT-AN-IBAN\",\"creditorIban\":\"DE89370400440532013000\","
                + "\"amount\":5,\"currency\":\"CHF\",\"referenceType\":\"NON\",\"reference\":\"\"}";
        Message message = MessageBuilder.withBody(garbage.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();

        rabbit.send(Topology.EXCHANGE, Topology.PAYMENT_RECEIVED, message);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(admin.getQueueInfo("processor.payment-received.dlq").getMessageCount()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_payment", Integer.class)).isZero();
    }
}
