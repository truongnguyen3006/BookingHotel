package com.example.bookinghotel.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(
        @NotBlank String method,
        boolean simulateFailure,
        @NotBlank String idempotencyKey
) {}
