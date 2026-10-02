package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentMethod;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PaymentStateMachineTest {

    @Test
    void pendingProviderPayment_canCompleteOnlyOnce() {
        BookingEntity booking = mock(BookingEntity.class);
        Instant createdAt = Instant.now();
        PaymentEntity payment = new PaymentEntity(
                booking,
                PaymentMethod.VNPAY,
                PaymentStatus.PENDING,
                null,
                "idem-1",
                "txn-ref-1",
                null,
                null,
                1_250_000L,
                null,
                createdAt
        );

        payment.completeProviderPayment(
                PaymentStatus.SUCCESS,
                "99887766",
                "00",
                "00",
                createdAt.plusSeconds(10)
        );

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertThrows(IllegalStateException.class, () -> payment.completeProviderPayment(
                PaymentStatus.FAILED,
                "99887766",
                "24",
                "02",
                null
        ));
    }

    @Test
    void providerCompletionCannotRemainPending() {
        BookingEntity booking = mock(BookingEntity.class);
        PaymentEntity payment = new PaymentEntity(
                booking,
                PaymentMethod.VNPAY,
                PaymentStatus.PENDING,
                null,
                "idem-2",
                "txn-ref-2",
                null,
                null,
                1_250_000L,
                null,
                Instant.now()
        );

        assertThrows(IllegalArgumentException.class, () -> payment.completeProviderPayment(
                PaymentStatus.PENDING,
                null,
                null,
                null,
                null
        ));
    }
}
