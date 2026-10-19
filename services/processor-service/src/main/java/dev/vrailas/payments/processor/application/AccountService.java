package dev.vrailas.payments.processor.application;

import dev.vrailas.payments.domain.Currency;
import dev.vrailas.payments.domain.DomainViolationException;
import dev.vrailas.payments.domain.Iban;
import dev.vrailas.payments.processor.adapter.out.persistence.AccountEntity;
import dev.vrailas.payments.processor.adapter.out.persistence.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Demo account management so that the ledger can be funded; a real bank would integrate its core banking system. */
@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public AccountEntity open(String iban, Currency currency, BigDecimal initialBalance) {
        Iban validated = new Iban(iban);
        if (currency == null || initialBalance == null || initialBalance.signum() < 0 || initialBalance.stripTrailingZeros().scale() > 2) {
            throw new DomainViolationException("currency and a non-negative balance with at most two decimals are required");
        }
        if (accounts.existsById(validated.value())) {
            throw new AccountAlreadyExistsException(validated.value());
        }
        return accounts.save(new AccountEntity(validated.value(), currency.name(), initialBalance.setScale(2, RoundingMode.UNNECESSARY)));
    }

    @Transactional(readOnly = true)
    public AccountEntity get(String iban) {
        String normalized = new Iban(iban).value();
        return accounts.findById(normalized).orElseThrow(() -> new AccountNotFoundException(normalized));
    }
}
