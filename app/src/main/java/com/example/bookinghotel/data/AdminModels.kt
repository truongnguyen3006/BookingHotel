package com.example.bookinghotel.data

data class AdminDashboard(
    val totalBookings: Long,
    val paidBookings: Long,
    val totalRevenue: Long,
    val roomTypes: Long,
    val availableRoomInventory: Long
)

data class AdminPayment(
    val status: String,
    val method: String,
    val transactionId: String?,
    val paidAt: Long?
)

data class AdminBooking(
    val bookingId: Int,
    val room: Room,
    val quantity: Int,
    val totalPrice: Long,
    val status: String,
    val checkInDate: Long,
    val checkOutDate: Long,
    val guests: Int,
    val nights: Int,
    val createdAt: Long,
    val userId: Long?,
    val userEmail: String?,
    val userDisplayName: String?,
    val payment: AdminPayment?
)
