package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.PaymentRequest;
import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceLifecycleTest {

    @Test
    void simulatedFailure_releasesInventoryAndPersistsFailedPayment() {
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        PaymentJpaRepository paymentRepository = mock(PaymentJpaRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        BookingLifecycleService lifecycle = mock(BookingLifecycleService.class);
        UserEntity user = mock(UserEntity.class);
        BookingEntity booking = mock(BookingEntity.class);

        when(user.getId()).thenReturn(9L);
        when(currentUserService.requireUser()).thenReturn(user);
        when(booking.getId()).thenReturn(42);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING_PAYMENT);
        when(bookingRepository.findOwnedByIdForUpdate(42, 9L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_IdAndIdempotencyKey(42, "card-fail-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(
                bookingRepository,
                paymentRepository,
                currentUserService,
                lifecycle
        );

        var response = service.pay(42, new PaymentRequest("CARD", true, "card-fail-1"));

        verify(lifecycle).ensureInventoryReservedForRetry(booking, null);
        verify(lifecycle).markProcessing(booking);
        verify(lifecycle).markFailedAndRelease(booking);

        ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.FAILED, paymentCaptor.getValue().getStatus());
        assertEquals("FAILED", response.status());
    }

    @Test
    void successfulDemoPayment_keepsInventoryConsumedAndPersistsSuccess() {
        BookingJpaRepository bookingRepository = mock(BookingJpaRepository.class);
        PaymentJpaRepository paymentRepository = mock(PaymentJpaRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        BookingLifecycleService lifecycle = mock(BookingLifecycleService.class);
        UserEntity user = mock(UserEntity.class);
        BookingEntity booking = mock(BookingEntity.class);

        when(user.getId()).thenReturn(10L);
        when(currentUserService.requireUser()).thenReturn(user);
        when(booking.getId()).thenReturn(43);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING_PAYMENT);
        when(bookingRepository.findOwnedByIdForUpdate(43, 10L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_IdAndIdempotencyKey(43, "qr-ok-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(
                bookingRepository,
                paymentRepository,
                currentUserService,
                lifecycle
        );

        var response = service.pay(43, new PaymentRequest("QR", false, "qr-ok-1"));

        verify(lifecycle).ensureInventoryReservedForRetry(booking, null);
        verify(lifecycle).markProcessing(booking);
        verify(lifecycle).markSuccessful(booking);

        ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(PaymentStatus.SUCCESS, paymentCaptor.getValue().getStatus());
        assertEquals("SUCCESS", response.status());
    }
}
