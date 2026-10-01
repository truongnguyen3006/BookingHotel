package com.example.bookinghotel.backend.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingRequest(
        @NotNull @Positive Integer roomId,
        @Positive int quantity,
        @Positive long checkInDate,
        @Positive long checkOutDate,
        @Positive int guests
) {}
