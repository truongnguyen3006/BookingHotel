package com.example.bookinghotel.data.remote.dto

data class AdminDashboardDto(
    val totalBookings: Long,
    val paidBookings: Long,
    val totalRevenue: Double,
    val roomTypes: Long,
    val availableRoomInventory: Long
)

data class AdminRoomUpdateRequestDto(
    val pricePerNight: Double? = null,
    val availableRooms: Int? = null
)

data class AdminPaymentDto(
    val status: String,
    val method: String,
    val transactionId: String?,
    val paidAt: Long?
)

data class AdminBookingDto(
    val bookingId: Int,
    val room: RoomDto,
    val quantity: Int,
    val totalPrice: Double,
    val status: String,
    val checkInDate: Long,
    val checkOutDate: Long,
    val guests: Int,
    val nights: Int,
    val createdAt: Long,
    val userId: Long?,
    val userEmail: String?,
    val userDisplayName: String?,
    val payment: AdminPaymentDto?
)
