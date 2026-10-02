package com.example.bookinghotel.data

data class Booking(
    val localId: Long = 0,
    val bookingId: Int,
    val roomId: Int,
    val roomTypeKey: String,
    val quantity: Int,
    /** Native VND snapshot captured by the backend when the booking is created. */
    val pricePerNight: Long,
    val totalPrice: Long,
    val status: String,
    val createdAt: Long,
    val checkInDate: Long = 0L,
    val checkOutDate: Long = 0L,
    val guests: Int = 1,
    val nights: Int = 1,
    val paymentMethod: String? = null,
    val transactionId: String? = null,
    val paidAt: Long? = null
)
