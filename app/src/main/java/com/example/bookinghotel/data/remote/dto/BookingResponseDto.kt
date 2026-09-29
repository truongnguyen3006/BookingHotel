package com.example.bookinghotel.data.remote.dto

data class BookingResponseDto(
    val bookingId: Int,
    val room: RoomDto,
    val quantity: Int,
    val totalPrice: Double,
    val status: String,
    val checkInDate: Long = 0L,
    val checkOutDate: Long = 0L,
    val guests: Int = 1,
    val nights: Int = 1
)
