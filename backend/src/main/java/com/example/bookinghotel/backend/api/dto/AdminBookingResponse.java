package com.example.bookinghotel.backend.api.dto;

public record AdminBookingResponse(
        int bookingId,
        RoomResponse room,
        int quantity,
        long totalPrice,
        String status,
        long checkInDate,
        long checkOutDate,
        int guests,
        int nights,
        long createdAt,
        Long userId,
        String userEmail,
        String userDisplayName,
        AdminPaymentResponse payment
) {}
