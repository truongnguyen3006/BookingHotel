package com.example.bookinghotel.backend.api.dto;

public record VnPayCreateResponse(
        int bookingId,
        String status,
        String paymentUrl,
        String txnRef,
        long amountVnd,
        long expiresAt
) {}
