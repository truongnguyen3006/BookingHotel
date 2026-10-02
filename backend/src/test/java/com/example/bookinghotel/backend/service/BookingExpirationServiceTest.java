package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingExpirationServiceTest {

    @Test
    void expiredPendingPayment_isFailedAndInventoryReleased() {
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        PaymentJpaRepository paymentRepository = mock(PaymentJpaRepository.class);
        BookingLifecycleService lifecycle = mock(BookingLifecycleService.class);
        BookingEntity booking = mock(BookingEntity.class);
        PaymentEntity payment = mock(PaymentEntity.class);
        Instant now = Instant.now();

        when(bookingRepository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.PROCESSING);
        when(booking.getReservationExpiresAt()).thenReturn(now.minusSeconds(1));
        when(paymentRepository.findLatestByBookingIdForUpdate(10)).thenReturn(Optional.of(payment));
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        BookingExpirationService service = new BookingExpirationService(
                bookingRepository,
                paymentRepository,
                lifecycle
        );
        service.expireIfDue(10, now);

        verify(payment).completeProviderPayment(
                PaymentStatus.FAILED,
                null,
                "EXPIRED",
                "EXPIRED",
                null
        );
        verify(lifecycle).markFailedAndRelease(booking);
    }

    @Test
    void completedPayment_neverReleasesInventory() {
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        PaymentJpaRepository paymentRepository = mock(PaymentJpaRepository.class);
        BookingLifecycleService lifecycle = mock(BookingLifecycleService.class);
        BookingEntity booking = mock(BookingEntity.class);
        PaymentEntity payment = mock(PaymentEntity.class);
        Instant now = Instant.now();

        when(bookingRepository.findByIdForUpdate(11)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.PROCESSING);
        when(booking.getReservationExpiresAt()).thenReturn(now.minusSeconds(1));
        when(paymentRepository.findLatestByBookingIdForUpdate(11)).thenReturn(Optional.of(payment));
        when(payment.getStatus()).thenReturn(PaymentStatus.SUCCESS);

        BookingExpirationService service = new BookingExpirationService(
                bookingRepository,
                paymentRepository,
                lifecycle
        );
        service.expireIfDue(11, now);

        verify(lifecycle).markSuccessful(booking);
        verify(lifecycle, never()).markFailedAndRelease(booking);
    }

    @Test
    void reservationNotDue_isLeftUntouched() {
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        PaymentJpaRepository paymentRepository = mock(PaymentJpaRepository.class);
        BookingLifecycleService lifecycle = mock(BookingLifecycleService.class);
        BookingEntity booking = mock(BookingEntity.class);
        Instant now = Instant.now();

        when(bookingRepository.findByIdForUpdate(12)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING_PAYMENT);
        when(booking.getReservationExpiresAt()).thenReturn(now.plusSeconds(60));

        BookingExpirationService service = new BookingExpirationService(
                bookingRepository,
                paymentRepository,
                lifecycle
        );
        service.expireIfDue(12, now);

        verify(paymentRepository, never()).findLatestByBookingIdForUpdate(12);
        verify(lifecycle, never()).markFailedAndRelease(booking);
    }
}
