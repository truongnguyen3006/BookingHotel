package com.example.bookinghotel.backend.api.dto;

import java.util.List;

public record RoomResponse(
        int id,
        String imageKey,
        String typeKey,
        long pricePerNight,
        List<String> amenities,
        int availableRooms
) {}
