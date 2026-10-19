package dev.vrailas.payments.ingest.domain;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.domain.Money;
import dev.vrailas.payments.domain.PaymentReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentInstructionTest {

    private static final Iban DEBTOR = new Iban("CH9300762011623852957");
    private static final Iban QR_IBAN = new Iban("CH4431999123000889012");
    private static final PaymentReference QRR = PaymentReference.of("QRR", "210000000003139471430009017");

    private static PaymentInstruction instruction(Iban creditor, PaymentReference reference, String amount) {
        return new PaymentInstruction(DEBTOR, creditor, Money.of(amount, Currency.CHF), reference, "Invoice 1");
    }

    @Test
    void qrIbanRequiresQrReference() {
        assertThatThrownBy(() -> instruction(QR_IBAN, null, "10"))
                .isInstanceOf(DomainViolationException.class).hasMessageContaining("requires a QR reference");
        assertThat(instruction(QR_IBAN, QRR, "10")).isNotNull();
    }

    @Test
    void regularIbanRejectsQrReference() {
        assertThatThrownBy(() -> instruction(new Iban("LI21088100002324013AA"), QRR, "10"))
                .isInstanceOf(DomainViolationException.class).hasMessageContaining("requires a QR-IBAN");
    }

    @Test
    void fingerprintIsStableAndSensitiveToContent() {
        PaymentInstruction a = instruction(QR_IBAN, QRR, "10.00");
        assertThat(a.fingerprint()).hasSize(64).isEqualTo(instruction(QR_IBAN, QRR, "10").fingerprint());
        assertThat(a.fingerprint()).isNotEqualTo(instruction(QR_IBAN, QRR, "10.01").fingerprint());
    }

    @Test
    void remittanceInformationIsLimited() {
        assertThatThrownBy(() -> new PaymentInstruction(DEBTOR, QR_IBAN, Money.of("1", Currency.CHF), QRR, "x".repeat(141)))
                .isInstanceOf(DomainViolationException.class);
    }
}
