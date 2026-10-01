package com.example.bookinghotel.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VnPayCreateRequest(
        @NotBlank @Size(max = 120) String idempotencyKey
) {}
