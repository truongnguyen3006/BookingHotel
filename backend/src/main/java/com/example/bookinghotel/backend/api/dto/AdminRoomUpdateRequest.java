package com.example.bookinghotel.backend.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record AdminRoomUpdateRequest(
        @DecimalMin(value = "0.01", message = "pricePerNight must be greater than 0")
        BigDecimal pricePerNight,
        @Min(value = 0, message = "availableRooms cannot be negative")
        Integer availableRooms
) {}
