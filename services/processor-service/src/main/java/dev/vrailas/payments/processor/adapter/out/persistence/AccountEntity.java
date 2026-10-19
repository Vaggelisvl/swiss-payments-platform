package dev.vrailas.payments.processor.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    private String iban;
    @Column(nullable = false, updatable = false)
    private String currency;
    @Column(nullable = false)
    private BigDecimal balance;
    @Version
    private long version;

    protected AccountEntity() {
    }

    public AccountEntity(String iban, String currency, BigDecimal balance) {
        this.iban = iban;
        this.currency = currency;
        this.balance = balance;
    }

    public boolean canCover(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        if (!canCover(amount)) {
            throw new IllegalStateException("insufficient funds on " + iban);
        }
        balance = balance.subtract(amount);
    }

    public String getIban() {
        return iban;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
