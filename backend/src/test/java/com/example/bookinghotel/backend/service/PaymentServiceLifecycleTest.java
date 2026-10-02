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
    void demoPaymentsDisabledByDefault() {
        PaymentService service = new PaymentService(mock(BookingJpaRepository.class), mock(PaymentJpaRepository.class),
                mock(CurrentUserService.class), mock(BookingLifecycleService.class));
        org.junit.jupiter.api.Assertions.assertThrows(com.example.bookinghotel.backend.exception.BadRequestException.class,
                () -> service.pay(42, new PaymentRequest("CARD", false, "disabled")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CARD", "QR"})
    void demoPaymentCannotOverlapPendingProvider(String method) {
        var bookings = mock(BookingJpaRepository.class);
        var payments = mock(PaymentJpaRepository.class);
        var users = mock(CurrentUserService.class);
        var lifecycle = mock(BookingLifecycleService.class);
        var user = mock(UserEntity.class);
        var booking = mock(BookingEntity.class);
        var active = mock(PaymentEntity.class);
        when(user.getId()).thenReturn(9L);
        when(users.requireUser()).thenReturn(user);
        when(booking.getStatus()).thenReturn(BookingStatus.PROCESSING);
        when(bookings.findOwnedByIdForUpdate(42, 9L)).thenReturn(Optional.of(booking));
        when(payments.findLatestByBookingIdForUpdate(42)).thenReturn(Optional.of(active));
        when(active.getStatus()).thenReturn(PaymentStatus.PENDING);
        PaymentService service = new PaymentService(bookings, payments, users, lifecycle);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "demoPaymentsEnabled", true);
        org.junit.jupiter.api.Assertions.assertThrows(com.example.bookinghotel.backend.exception.ConflictException.class,
                () -> service.pay(42, new PaymentRequest(method, false, "retry")));
        org.mockito.Mockito.verifyNoInteractions(lifecycle);
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).save(any());
    }


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

        org.springframework.test.util.ReflectionTestUtils.setField(service, "demoPaymentsEnabled", true);
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

        org.springframework.test.util.ReflectionTestUtils.setField(service, "demoPaymentsEnabled", true);
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
