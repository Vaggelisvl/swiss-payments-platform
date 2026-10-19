package dev.vrailas.payments.reconciliation.adapter.out.persistence;

import dev.vrailas.payments.reconciliation.domain.CreditResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CreditEntryRepository extends JpaRepository<CreditEntryEntity, String> {

    List<CreditEntryEntity> findByResultInOrderByCreatedAt(Collection<CreditResult> results);
}
