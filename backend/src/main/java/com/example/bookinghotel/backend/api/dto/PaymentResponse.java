package com.example.bookinghotel.backend.api.dto;

public record PaymentResponse(
        int bookingId,
        String status,
        String method,
        String transactionId,
        String message,
        Long paidAt
) {}
