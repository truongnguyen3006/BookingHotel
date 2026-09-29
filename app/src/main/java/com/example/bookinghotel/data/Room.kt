package com.example.bookinghotel.data

data class Room(
    val id: Int,
    val image: Int,
    val type: Int,
    val pricePerNight: Double,
    // tiện nghi
    val amenities: List<String>,
    val availableRooms: Int
)

