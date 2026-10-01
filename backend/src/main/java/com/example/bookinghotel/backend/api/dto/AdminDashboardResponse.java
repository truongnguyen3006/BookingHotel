package com.example.bookinghotel.backend.api.dto;

public record AdminDashboardResponse(
        long totalBookings,
        long paidBookings,
        double totalRevenue,
        long roomTypes,
        long availableRoomInventory
) {}
