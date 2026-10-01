package com.example.bookinghotel.backend.api.dto;

public record VnPayStatusResponse(
        int bookingId,
        String status,
        String method,
        String transactionId,
        String txnRef,
        long amountVnd,
        String responseCode,
        String message,
        Long paidAt
) {}
