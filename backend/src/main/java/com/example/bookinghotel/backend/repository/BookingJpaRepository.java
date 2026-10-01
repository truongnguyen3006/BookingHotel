package com.example.bookinghotel.backend.repository;

import com.example.bookinghotel.backend.domain.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingJpaRepository extends JpaRepository<BookingEntity, Integer> {
    Optional<BookingEntity> findByIdAndUser_Id(Integer id, Long userId);
    List<BookingEntity> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
    List<BookingEntity> findAllByOrderByCreatedAtDesc();
}
