package com.example.bookinghotel.backend.repository;

import com.example.bookinghotel.backend.domain.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByBooking_IdAndIdempotencyKey(Integer bookingId, String idempotencyKey);
    Optional<PaymentEntity> findFirstByBooking_IdOrderByCreatedAtDesc(Integer bookingId);
    Optional<PaymentEntity> findByProviderReference(String providerReference);

    // Discover only the ID: loading the payment before acquiring the booking lock
    // could leave stale PENDING state in Hibernate's first-level cache after waiting.
    @Query("select p.booking.id from PaymentEntity p where p.providerReference = :providerReference")
    Optional<Integer> findBookingIdByProviderReference(@Param("providerReference") String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = (select max(p2.id) from PaymentEntity p2 where p2.booking.id = :bookingId)")
    Optional<PaymentEntity> findLatestByBookingIdForUpdate(@Param("bookingId") Integer bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p join fetch p.booking where p.providerReference = :providerReference")
    Optional<PaymentEntity> findByProviderReferenceForUpdate(@Param("providerReference") String providerReference);
}
