package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BookingExpirationService {
    private final BookingJpaRepository bookingRepository;
    private final PaymentJpaRepository paymentRepository;
    private final BookingLifecycleService bookingLifecycleService;

    public BookingExpirationService(
            BookingJpaRepository bookingRepository,
            PaymentJpaRepository paymentRepository,
            BookingLifecycleService bookingLifecycleService
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.bookingLifecycleService = bookingLifecycleService;
    }

    /**
     * Expires one reservation under a booking-row lock. The latest payment is also
     * locked before being changed, making this safe against concurrent VNPAY IPNs.
     */
    @Transactional
    public void expireIfDue(int bookingId, Instant now) {
        BookingEntity booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null) return;
        if (booking.getStatus() == BookingStatus.SUCCESS || booking.getStatus() == BookingStatus.FAILED) return;
        if (booking.getReservationExpiresAt() == null || booking.getReservationExpiresAt().isAfter(now)) return;

        PaymentEntity latest = paymentRepository.findLatestByBookingIdForUpdate(bookingId).orElse(null);
        if (latest != null && latest.getStatus() == PaymentStatus.SUCCESS) {
            bookingLifecycleService.markSuccessful(booking);
            return;
        }

        if (latest != null && latest.getStatus() == PaymentStatus.PENDING) {
            latest.completeProviderPayment(
                    PaymentStatus.FAILED,
                    latest.getTransactionId(),
                    "EXPIRED",
                    "EXPIRED",
                    null
            );
        }

        bookingLifecycleService.markFailedAndRelease(booking);
    }
}
