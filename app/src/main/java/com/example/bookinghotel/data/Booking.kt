package com.example.bookinghotel.data

data class Booking(
    val localId: Long = 0,
    val bookingId: Int,
    val roomId: Int,
    val roomTypeKey: String,
    val quantity: Int,
    val pricePerNight: Double,
    val totalPrice: Double,
    val status: String,
    val createdAt: Long
)
