package com.example.bookinghotel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_cache")
data class RoomCacheEntity(
    @PrimaryKey val id: Int,
    val imageKey: String,
    val typeKey: String,
    val pricePerNight: Long,
    val amenities: List<String>,
    val availableRooms: Int,
    val lastUpdatedAt: Long
)
