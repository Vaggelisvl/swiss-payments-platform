package dev.vrailas.payments.processor.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedPaymentRepository extends JpaRepository<ProcessedPaymentEntity, UUID> {
}
