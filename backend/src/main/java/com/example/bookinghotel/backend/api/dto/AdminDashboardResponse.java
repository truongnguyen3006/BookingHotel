package com.example.bookinghotel.backend.api.dto;

public record AdminDashboardResponse(
        long totalBookings,
        long paidBookings,
        long totalRevenue,
        long roomTypes,
        long availableRoomInventory
) {}
