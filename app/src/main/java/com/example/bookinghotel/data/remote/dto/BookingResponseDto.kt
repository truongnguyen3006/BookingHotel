package com.example.bookinghotel.data.remote.dto

data class BookingResponseDto(
    val bookingId: Int,
    val room: RoomDto,
    val quantity: Int,
    val totalPrice: Double,
    val status: String
)
