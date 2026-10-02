package com.example.bookinghotel.backend.api.dto;

import jakarta.validation.constraints.Min;

public record AdminRoomUpdateRequest(
        @Min(value = 1, message = "pricePerNight must be at least 1 VND")
        Long pricePerNight,
        @Min(value = 0, message = "availableRooms cannot be negative")
        Integer availableRooms
) {}
