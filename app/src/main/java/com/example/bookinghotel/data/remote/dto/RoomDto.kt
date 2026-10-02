package com.example.bookinghotel.data.remote.dto

data class RoomDto(
    val id: Int,
    val imageKey: String,
    val typeKey: String,
    val pricePerNight: Long,
    val amenities: List<String>,
    val availableRooms: Int
)
