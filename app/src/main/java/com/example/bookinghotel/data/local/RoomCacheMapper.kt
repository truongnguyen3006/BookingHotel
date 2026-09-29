package com.example.bookinghotel.data.local

import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.roomImageKey
import com.example.bookinghotel.data.roomImageResource
import com.example.bookinghotel.data.roomTypeResource

fun RoomCacheEntity.toDomain(): Room {
    return Room(
        id = id,
        image = roomImageResource(imageKey),
        type = roomTypeResource(typeKey),
        typeKey = typeKey,
        pricePerNight = pricePerNight,
        amenities = amenities,
        availableRooms = availableRooms
    )
}

fun Room.toCacheEntity(lastUpdatedAt: Long = System.currentTimeMillis()): RoomCacheEntity {
    return RoomCacheEntity(
        id = id,
        imageKey = roomImageKey(typeKey),
        typeKey = typeKey,
        pricePerNight = pricePerNight,
        amenities = amenities,
        availableRooms = availableRooms,
        lastUpdatedAt = lastUpdatedAt
    )
}
