package dev.vrailas.payments.assistant.domain;

import dev.vrailas.payments.assistant.domain.Explanation.Origin;

import java.util.Map;

/** Known exception codes of the processor and reconciliation services. The fallback when no model is available, and the grounding for one. */
public final class Catalogue {

    private static final Map<String, Explanation> KNOWN = Map.ofEntries(
            entry("AMOUNT_LIMIT_EXCEEDED", "The payment amount is above the configured per-payment limit.",
                    "Check the amount with the customer. If it is legitimate, split the payment or have it approved under the manual limit process.", Severity.MEDIUM),
            entry("DENYLISTED_ACCOUNT", "Debtor or creditor account is on the deny list.",
                    "Do not release the payment. Escalate to compliance for review before any contact with the customer.", Severity.HIGH),
            entry("SAME_ACCOUNT", "Debtor and creditor are the same account.",
                    "Ask the customer to correct the creditor account; this is usually an input mistake.", Severity.LOW),
            entry("UNKNOWN_DEBTOR", "The debtor account is not known to the ledger.",
                    "Verify the debtor IBAN in the source file; if it is correct, the account has to be created before the payment is resubmitted.", Severity.MEDIUM),
            entry("CURRENCY_MISMATCH", "The payment currency differs from the debtor account currency.",
                    "Resubmit in the account currency or arrange a currency conversion.", Severity.MEDIUM),
            entry("INSUFFICIENT_FUNDS", "The debtor account balance does not cover the payment.",
                    "Notify the customer. The payment can be resubmitted once the account is funded.", Severity.LOW),
            entry("UNMATCHED", "An incoming credit carries no reference, or a reference that matches no open invoice.",
                    "Look up the debtor and amount for a likely invoice and assign the credit manually; otherwise return the funds.", Severity.MEDIUM),
            entry("CURRENCY_MISMATCH_CREDIT", "The credit matches an invoice that is denominated in another currency.",
                    "Contact the payer. Do not mark the invoice as paid until the currency is clarified.", Severity.MEDIUM),
            entry("OVERPAID", "The invoice received more than its amount.",
                    "Refund the difference or keep it as a credit balance, as agreed with the creditor.", Severity.MEDIUM),
            entry("PARTIAL", "The invoice is not fully paid yet.",
                    "No action is needed unless the due date has passed; then remind the payer of the open amount.", Severity.LOW));

    private static final Explanation UNKNOWN = new Explanation("This exception code is not in the catalogue.",
            "Check the service logs for this code and escalate to the owning team.", Severity.MEDIUM, Origin.CATALOGUE);

    private Catalogue() {
    }

    /** Reconciliation reuses "CURRENCY_MISMATCH" for credits, so the source disambiguates. */
    public static Explanation lookup(ExceptionCase exceptionCase) {
        String code = exceptionCase.code().trim().toUpperCase();
        if ("reconciliation".equalsIgnoreCase(exceptionCase.source()) && code.equals("CURRENCY_MISMATCH")) {
            code = "CURRENCY_MISMATCH_CREDIT";
        }
        return KNOWN.getOrDefault(code, UNKNOWN);
    }

    private static Map.Entry<String, Explanation> entry(String code, String cause, String action, Severity severity) {
        return Map.entry(code, new Explanation(cause, action, severity, Origin.CATALOGUE));
    }
}
