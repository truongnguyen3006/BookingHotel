package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.PaymentRequest;
import com.example.bookinghotel.backend.api.dto.PaymentResponse;
import com.example.bookinghotel.backend.domain.BookingEntity;
import com.example.bookinghotel.backend.domain.BookingStatus;
import com.example.bookinghotel.backend.domain.PaymentEntity;
import com.example.bookinghotel.backend.domain.PaymentMethod;
import com.example.bookinghotel.backend.domain.PaymentStatus;
import com.example.bookinghotel.backend.exception.BadRequestException;
import com.example.bookinghotel.backend.exception.ConflictException;
import com.example.bookinghotel.backend.exception.NotFoundException;
import com.example.bookinghotel.backend.repository.BookingJpaRepository;
import com.example.bookinghotel.backend.repository.PaymentJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {
    private final BookingJpaRepository bookingRepository;
    private final PaymentJpaRepository paymentRepository;
    private final CurrentUserService currentUserService;

    public PaymentService(
            BookingJpaRepository bookingRepository,
            PaymentJpaRepository paymentRepository,
            CurrentUserService currentUserService
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public PaymentResponse pay(int bookingId, PaymentRequest request) {
        Long userId = currentUserService.requireUser().getId();
        BookingEntity booking = bookingRepository.findByIdAndUser_Id(bookingId, userId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));

        var existing = paymentRepository.findByBooking_IdAndIdempotencyKey(bookingId, request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get(), "Idempotent payment result");
        }

        if (booking.getStatus() == BookingStatus.SUCCESS) {
            throw new ConflictException("BOOKING_ALREADY_PAID", "Booking already paid");
        }

        PaymentMethod method = parseMethod(request.method());
        booking.setStatus(BookingStatus.PROCESSING);
        bookingRepository.save(booking);

        Instant now = Instant.now();
        if (request.simulateFailure()) {
            booking.setStatus(BookingStatus.FAILED);
            PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                    booking,
                    method,
                    PaymentStatus.FAILED,
                    null,
                    request.idempotencyKey(),
                    null,
                    now
            ));
            return toResponse(payment, "Payment was declined (simulated)");
        }

        String transactionId = "TXN-" + bookingId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        booking.setStatus(BookingStatus.SUCCESS);
        PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                booking,
                method,
                PaymentStatus.SUCCESS,
                transactionId,
                request.idempotencyKey(),
                now,
                now
        ));

        return toResponse(payment, "Payment completed successfully");
    }

    private PaymentMethod parseMethod(String rawMethod) {
        try {
            return PaymentMethod.valueOf(rawMethod.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BadRequestException("UNSUPPORTED_PAYMENT_METHOD", "Unsupported payment method");
        }
    }

    private PaymentResponse toResponse(PaymentEntity payment, String message) {
        return new PaymentResponse(
                payment.getBooking().getId(),
                payment.getStatus().name(),
                payment.getMethod().name(),
                payment.getTransactionId(),
                message,
                payment.getPaidAt() == null ? null : payment.getPaidAt().toEpochMilli()
        );
    }
}
