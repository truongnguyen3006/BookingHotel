package com.example.bookinghotel.data

data class Room(
    val id: Int,
    val image: Int,
    val type: Int,
    val typeKey: String,
    /** Native VND amount. */
    val pricePerNight: Long,
    val amenities: List<String>,
    val availableRooms: Int
)
