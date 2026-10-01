package com.example.bookinghotel.backend.api.dto;

public record AdminPaymentResponse(
        String status,
        String method,
        String transactionId,
        Long paidAt
) {}
