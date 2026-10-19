package dev.vrailas.payments.ingest.adapter.in.web;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Streaming reader for the subset of ISO 20022 {@code pain.001} (customer credit transfer initiation) that
 * is needed to create payments. Namespace and version agnostic (matches on local element names).
 *
 * <p>The XML parser is hardened against XXE and entity expansion attacks: DTDs and external entities are disabled.
 */
final class Pain001Parser {

    /** One {@code CdtTrfTxInf} element, resolved against its message and payment information block. */
    record Transaction(
            String messageId,
            String endToEndId,
            String debtorIban,
            String creditorIban,
            String amount,
            String currency,
            String referenceType,
            String reference,
            String remittanceInformation) {

        /** Idempotency key of the transaction: message id plus end-to-end id. */
        String idempotencyKey() {
            return messageId + ":" + endToEndId;
        }
    }

    private static final XMLInputFactory FACTORY = hardenedFactory();

    private static XMLInputFactory hardenedFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    /** Mutable collector for the transaction that is currently being read. */
    private static final class Draft {
        String endToEndId;
        String creditorIban;
        String amount;
        String currency;
        String referenceType;
        String reference;
        String remittance;
    }

    List<Transaction> parse(InputStream xml) {
        List<Transaction> result = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        StringBuilder text = new StringBuilder();
        String messageId = null;
        String debtorIban = null;
        Draft draft = null;
        String pendingCurrency = null;

        try {
            XMLStreamReader reader = FACTORY.createXMLStreamReader(xml);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = reader.getLocalName();
                        path.addLast(name);
                        text.setLength(0);
                        if ("PmtInf".equals(name)) {
                            debtorIban = null;
                        } else if ("CdtTrfTxInf".equals(name)) {
                            draft = new Draft();
                        } else if ("InstdAmt".equals(name)) {
                            pendingCurrency = reader.getAttributeValue(null, "Ccy");
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        text.append(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        String value = text.toString().strip();
                        String where = String.join("/", path);
                        String name = path.removeLast();
                        text.setLength(0);

                        if (where.endsWith("GrpHdr/MsgId")) {
                            messageId = value;
                        } else if (where.endsWith("PmtInf/DbtrAcct/Id/IBAN")) {
                            debtorIban = value;
                        } else if (draft != null) {
                            if (where.endsWith("PmtId/EndToEndId")) {
                                draft.endToEndId = value;
                            } else if (where.endsWith("CdtTrfTxInf/Amt/InstdAmt")) {
                                draft.amount = value;
                                draft.currency = pendingCurrency;
                            } else if (where.endsWith("CdtrAcct/Id/IBAN")) {
                                draft.creditorIban = value;
                            } else if (where.endsWith("CdtrRefInf/Tp/CdOrPrtry/Prtry") || where.endsWith("CdtrRefInf/Tp/CdOrPrtry/Cd")) {
                                draft.referenceType = value;
                            } else if (where.endsWith("CdtrRefInf/Ref")) {
                                draft.reference = value;
                            } else if (where.endsWith("RmtInf/Ustrd")) {
                                draft.remittance = value;
                            } else if ("CdtTrfTxInf".equals(name)) {
                                result.add(finish(messageId, debtorIban, draft));
                                draft = null;
                            }
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new Pain001Exception("pain.001 document is not well-formed XML", e);
        }
        if (result.isEmpty()) {
            throw new Pain001Exception("pain.001 document contains no CdtTrfTxInf");
        }
        return result;
    }

    private static Transaction finish(String messageId, String debtorIban, Draft d) {
        require(messageId, "GrpHdr/MsgId");
        require(debtorIban, "PmtInf/DbtrAcct/Id/IBAN");
        require(d.endToEndId, "PmtId/EndToEndId");
        require(d.creditorIban, "CdtrAcct/Id/IBAN");
        require(d.amount, "Amt/InstdAmt");
        require(d.currency, "Amt/InstdAmt@Ccy");
        return new Transaction(messageId, d.endToEndId, debtorIban, d.creditorIban, d.amount, d.currency,
                d.referenceType, d.reference, d.remittance);
    }

    private static void require(String value, String element) {
        if (value == null || value.isBlank()) {
            throw new Pain001Exception("pain.001 is missing mandatory element " + element);
        }
    }
}
