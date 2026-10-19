package dev.vrailas.payments.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainTest {

    @Test
    void ibanIsNormalizedAndValidated() {
        assertThat(new Iban("ch93 0076 2011 6238 5295 7").value()).isEqualTo("CH9300762011623852957");
        assertThat(new Iban("DE89 3704 0044 0532 0130 00").value()).isEqualTo("DE89370400440532013000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CH9300762011623852958", "XX", "1234"})
    void ibanRejectsInvalidValues(String value) {
        assertThatThrownBy(() -> new Iban(value)).isInstanceOf(DomainViolationException.class);
    }

    @Test
    void recognizesQrIban() {
        assertThat(new Iban("CH4431999123000889012").isQrIban()).isTrue();
        assertThat(new Iban("CH9300762011623852957").isQrIban()).isFalse();
        assertThat(new Iban("DE89370400440532013000").isQrIban()).isFalse();
    }

    @Test
    void moneyKeepsTwoDecimalsAndRejectsInvalid() {
        assertThat(Money.of("10.5", Currency.CHF).amount()).hasToString("10.50");
        assertThatThrownBy(() -> Money.of("0", Currency.CHF)).isInstanceOf(DomainViolationException.class);
        assertThatThrownBy(() -> Money.of("1.001", Currency.CHF)).isInstanceOf(DomainViolationException.class);
        assertThatThrownBy(() -> Money.of("-5", Currency.EUR)).isInstanceOf(DomainViolationException.class);
        assertThatThrownBy(() -> new Money(null, Currency.EUR)).isInstanceOf(DomainViolationException.class);
    }

    @Test
    void referencesAreValidatedByType() {
        assertThat(PaymentReference.of("QRR", "21 00000 00003 13947 14300 09017").value()).isEqualTo("210000000003139471430009017");
        assertThat(PaymentReference.of("scor", "RF18 5390 0754 7034")).isInstanceOf(CreditorReference.class);
        assertThat(PaymentReference.of(null, null)).isInstanceOf(NoReference.class);
        assertThat(PaymentReference.of("NON", "ignored").value()).isEmpty();
        assertThatThrownBy(() -> PaymentReference.of("QRR", "210000000003139471430009018")).isInstanceOf(DomainViolationException.class);
        assertThatThrownBy(() -> PaymentReference.of("RF", "x")).isInstanceOf(DomainViolationException.class);
        assertThatThrownBy(() -> PaymentReference.of("SCOR", "RF19539007547034")).isInstanceOf(DomainViolationException.class);
    }
}
