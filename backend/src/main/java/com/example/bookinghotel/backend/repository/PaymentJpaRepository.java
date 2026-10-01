package com.example.bookinghotel.backend.repository;

import com.example.bookinghotel.backend.domain.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByBooking_IdAndIdempotencyKey(Integer bookingId, String idempotencyKey);
    Optional<PaymentEntity> findFirstByBooking_IdOrderByCreatedAtDesc(Integer bookingId);
}
