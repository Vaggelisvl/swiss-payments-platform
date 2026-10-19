package dev.vrailas.payments.reconciliation.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    Optional<InvoiceEntity> findByReference(String reference);

    /** Locks the invoice so that concurrent credits for it are applied one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvoiceEntity i where i.reference = :reference")
    Optional<InvoiceEntity> findForUpdateByReference(@Param("reference") String reference);
}
