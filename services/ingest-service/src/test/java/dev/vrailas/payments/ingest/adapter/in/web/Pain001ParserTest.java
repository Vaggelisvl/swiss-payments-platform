package dev.vrailas.payments.ingest.adapter.in.web;

import dev.vrailas.payments.ingest.adapter.in.web.Pain001Parser.Transaction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pain001ParserTest {

    private final Pain001Parser parser = new Pain001Parser();

    @Test
    void parsesAllCreditTransfers() throws IOException {
        try (InputStream xml = getClass().getResourceAsStream("/pain001-sample.xml")) {
            List<Transaction> transactions = parser.parse(xml);

            assertThat(transactions).hasSize(2);
            Transaction first = transactions.get(0);
            assertThat(first.idempotencyKey()).isEqualTo("MSG-2026-0001:E2E-1");
            assertThat(first.debtorIban()).isEqualTo("CH9300762011623852957");
            assertThat(first.creditorIban()).isEqualTo("CH4431999123000889012");
            assertThat(first.amount()).isEqualTo("1949.75");
            assertThat(first.currency()).isEqualTo("CHF");
            assertThat(first.referenceType()).isEqualTo("QRR");
            assertThat(first.reference()).isEqualTo("210000000003139471430009017");

            Transaction second = transactions.get(1);
            assertThat(second.idempotencyKey()).isEqualTo("MSG-2026-0001:E2E-2");
            assertThat(second.currency()).isEqualTo("EUR");
            assertThat(second.referenceType()).isEqualTo("SCOR");
        }
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parser.parse(stream("<Document><unclosed>")))
                .isInstanceOf(Pain001Exception.class).hasMessageContaining("not well-formed");
    }

    @Test
    void rejectsDocumentWithoutTransactions() {
        assertThatThrownBy(() -> parser.parse(stream("<Document><CstmrCdtTrfInitn><GrpHdr><MsgId>1</MsgId></GrpHdr></CstmrCdtTrfInitn></Document>")))
                .isInstanceOf(Pain001Exception.class).hasMessageContaining("no CdtTrfTxInf");
    }

    @Test
    void rejectsTransactionWithMissingMandatoryElement() {
        String xml = """
                <Document><CstmrCdtTrfInitn><GrpHdr><MsgId>1</MsgId></GrpHdr>
                <PmtInf><DbtrAcct><Id><IBAN>CH9300762011623852957</IBAN></Id></DbtrAcct>
                <CdtTrfTxInf><PmtId><EndToEndId>A</EndToEndId></PmtId></CdtTrfTxInf></PmtInf></CstmrCdtTrfInitn></Document>""";
        assertThatThrownBy(() -> parser.parse(stream(xml)))
                .isInstanceOf(Pain001Exception.class).hasMessageContaining("CdtrAcct/Id/IBAN");
    }

    @Test
    void neverResolvesExternalEntities() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document><CstmrCdtTrfInitn><GrpHdr><MsgId>1</MsgId></GrpHdr>
                <PmtInf><DbtrAcct><Id><IBAN>CH9300762011623852957</IBAN></Id></DbtrAcct>
                <CdtTrfTxInf><PmtId><EndToEndId>A</EndToEndId></PmtId><Amt><InstdAmt Ccy="CHF">1.00</InstdAmt></Amt>
                <CdtrAcct><Id><IBAN>CH9300762011623852957</IBAN></Id></CdtrAcct>
                <RmtInf><Ustrd>&xxe;</Ustrd></RmtInf></CdtTrfTxInf></PmtInf></CstmrCdtTrfInitn></Document>""";
        try {
            List<Transaction> transactions = parser.parse(stream(xml));
            assertThat(transactions.toString()).doesNotContain("root:");
        } catch (Pain001Exception expected) {
            assertThat(expected).hasMessageContaining("not well-formed");
        }
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
