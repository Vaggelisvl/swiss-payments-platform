package dev.vrailas.payments.reconciliation.adapter.in.web;

import dev.vrailas.payments.reconciliation.domain.Credit;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Streaming reader for ISO 20022 {@code camt.054} (debit/credit notification). Only credit entries ({@code CRDT})
 * become {@link Credit}s; debits are ignored. The XML parser is hardened against XXE (no DTDs, no external entities).
 */
final class Camt054Parser {

    private static final XMLInputFactory FACTORY = hardenedFactory();

    private static XMLInputFactory hardenedFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    private static final class Draft {
        String amount;
        String currency;
        String indicator;
        String bookedOn;
        String accountServicerRef;
        String entryRef;
        String reference;
    }

    List<Credit> parse(InputStream xml) {
        List<Credit> credits = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        StringBuilder text = new StringBuilder();
        String notificationId = "camt";
        int index = 0;
        Draft draft = null;
        String pendingCurrency = null;
        boolean sawEntry = false;

        try {
            XMLStreamReader reader = FACTORY.createXMLStreamReader(xml);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = reader.getLocalName();
                        path.addLast(name);
                        text.setLength(0);
                        if ("Ntry".equals(name)) {
                            draft = new Draft();
                            sawEntry = true;
                            index++;
                        } else if ("Amt".equals(name)) {
                            pendingCurrency = reader.getAttributeValue(null, "Ccy");
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        text.append(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        String value = text.toString().strip();
                        String where = String.join("/", path);
                        String name = path.removeLast();
                        text.setLength(0);

                        if (where.endsWith("Ntfctn/Id")) {
                            notificationId = value;
                        } else if (draft != null) {
                            if (where.endsWith("Ntry/Amt")) {
                                draft.amount = value;
                                draft.currency = pendingCurrency;
                            } else if (where.endsWith("Ntry/CdtDbtInd")) {
                                draft.indicator = value;
                            } else if (where.endsWith("Ntry/BookgDt/Dt")) {
                                draft.bookedOn = value;
                            } else if (where.endsWith("Ntry/AcctSvcrRef")) {
                                draft.accountServicerRef = value;
                            } else if (where.endsWith("Ntry/NtryRef")) {
                                draft.entryRef = value;
                            } else if (where.endsWith("CdtrRefInf/Ref") && draft.reference == null) {
                                draft.reference = value;
                            } else if ("Ntry".equals(name)) {
                                if ("CRDT".equals(draft.indicator)) {
                                    credits.add(toCredit(draft, notificationId, index));
                                }
                                draft = null;
                            }
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new Camt054Exception("camt.054 document is not well-formed XML", e);
        }
        if (!sawEntry) {
            throw new Camt054Exception("camt.054 document contains no Ntry");
        }
        return credits;
    }

    private static Credit toCredit(Draft d, String notificationId, int index) {
        if (d.amount == null || d.currency == null) {
            throw new Camt054Exception("camt.054 entry " + index + " has no amount or currency");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(d.amount);
        } catch (NumberFormatException e) {
            throw new Camt054Exception("camt.054 entry " + index + " has an invalid amount");
        }
        if (amount.signum() <= 0) {
            throw new Camt054Exception("camt.054 entry " + index + " has a non-positive amount");
        }
        LocalDate bookedOn = null;
        if (d.bookedOn != null) {
            try {
                bookedOn = LocalDate.parse(d.bookedOn.length() > 10 ? d.bookedOn.substring(0, 10) : d.bookedOn);
            } catch (DateTimeParseException e) {
                throw new Camt054Exception("camt.054 entry " + index + " has an invalid booking date");
            }
        }
        String ref = d.accountServicerRef != null ? d.accountServicerRef : d.entryRef;
        String entryId = "camt:" + (ref != null ? ref : notificationId + "#" + index);
        String reference = d.reference == null ? null : d.reference.replaceAll("\\s+", "");
        return new Credit(entryId, Credit.Source.CAMT054, reference, amount, d.currency, bookedOn);
    }
}
