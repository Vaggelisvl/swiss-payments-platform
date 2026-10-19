package dev.vrailas.payments.reconciliation.adapter.in.web;

import dev.vrailas.payments.reconciliation.domain.Credit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Camt054ParserTest {

    private final Camt054Parser parser = new Camt054Parser();

    private static String sample() throws IOException {
        try (InputStream in = Camt054ParserTest.class.getResourceAsStream("/camt054-sample.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${RUN}", "T1").replace("${AMOUNT1}", "1949.75")
                    .replace("${REF1}", "21 00000 00003 13947 14300 09017").replace("${REF2}", "RF18539007547034");
        }
    }

    @Test
    void readsCreditsAndIgnoresDebits() throws IOException {
        List<Credit> credits = parser.parse(stream(sample()));

        assertThat(credits).hasSize(2);
        Credit first = credits.get(0);
        assertThat(first.entryId()).isEqualTo("camt:ASR-T1-1");
        assertThat(first.source()).isEqualTo(Credit.Source.CAMT054);
        assertThat(first.reference()).isEqualTo("210000000003139471430009017");
        assertThat(first.amount()).isEqualByComparingTo("1949.75");
        assertThat(first.currency()).isEqualTo("CHF");
        assertThat(first.bookedOn()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void entriesWithoutServicerReferenceGetAStableDerivedId() throws IOException {
        List<Credit> credits = parser.parse(stream(sample()));

        assertThat(credits.get(1).entryId()).isEqualTo("camt:NTF-T1#3");
        assertThat(credits.get(1).reference()).isEqualTo("RF18539007547034");
    }

    @Test
    void rejectsMalformedAndEmptyDocuments() {
        assertThatThrownBy(() -> parser.parse(stream("<Document><broken>"))).isInstanceOf(Camt054Exception.class).hasMessageContaining("not well-formed");
        assertThatThrownBy(() -> parser.parse(stream("<Document/>"))).isInstanceOf(Camt054Exception.class).hasMessageContaining("no Ntry");
    }

    @Test
    void rejectsInvalidAmounts() {
        String xml = "<Document><Ntfctn><Id>1</Id><Ntry><Amt Ccy=\"CHF\">abc</Amt><CdtDbtInd>CRDT</CdtDbtInd></Ntry></Ntfctn></Document>";
        assertThatThrownBy(() -> parser.parse(stream(xml))).isInstanceOf(Camt054Exception.class).hasMessageContaining("invalid amount");
    }

    @Test
    void neverResolvesExternalEntities() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<Document><Ntfctn><Id>&xxe;</Id><Ntry><Amt Ccy=\"CHF\">1</Amt><CdtDbtInd>CRDT</CdtDbtInd></Ntry></Ntfctn></Document>";
        try {
            assertThat(parser.parse(stream(xml)).toString()).doesNotContain("root:");
        } catch (Camt054Exception expected) {
            assertThat(expected).hasMessageContaining("not well-formed");
        }
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
