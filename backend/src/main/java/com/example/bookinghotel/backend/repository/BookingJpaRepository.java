package com.example.bookinghotel.backend.repository;

import com.example.bookinghotel.backend.domain.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, Integer> {
    Optional<BookingEntity> findByIdAndUser_Id(Integer id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id and b.user.id = :userId")
    Optional<BookingEntity> findOwnedByIdForUpdate(
            @Param("id") Integer id,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") Integer id);
    List<BookingEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
    List<BookingEntity> findAllByOrderByCreatedAtDesc();

    @Query("select b.id from BookingEntity b where b.status in (com.example.bookinghotel.backend.domain.BookingStatus.PENDING_PAYMENT, com.example.bookinghotel.backend.domain.BookingStatus.PROCESSING) and b.reservationExpiresAt is not null and b.reservationExpiresAt <= :now")
    List<Integer> findExpiredReservationIds(@Param("now") Instant now);
}
