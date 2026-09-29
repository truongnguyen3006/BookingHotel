package com.example.bookinghotel.data.remote.dto

data class BookingRequestDto(
    val roomId: Int,
    val quantity: Int,
    val checkInDate: Long,
    val checkOutDate: Long,
    val guests: Int
)
