package dev.vrailas.payments.processor.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {

    /** Row lock ({@code SELECT ... FOR UPDATE}): concurrent debits of one account are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.iban = :iban")
    Optional<AccountEntity> findForUpdate(@Param("iban") String iban);

    /** Atomic credit that needs no read-modify-write, so crediting never contends with debiting. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountEntity a set a.balance = a.balance + :amount where a.iban = :iban and a.currency = :currency")
    int credit(@Param("iban") String iban, @Param("currency") String currency, @Param("amount") BigDecimal amount);
}
