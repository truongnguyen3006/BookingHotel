package com.example.bookinghotel.backend.api.dto;

public record BookingResponse(
        int bookingId,
        RoomResponse room,
        int quantity,
        long totalPrice,
        String status,
        long checkInDate,
        long checkOutDate,
        int guests,
        int nights,
        long createdAt
) {}
