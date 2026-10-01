package com.example.bookinghotel.backend.api.dto;

public record UserResponse(
        long id,
        String email,
        String displayName,
        String role
) {}
