package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.remote.dto.RoomDto
import com.example.bookinghotel.data.roomImageResource
import com.example.bookinghotel.data.roomTypeResource

fun RoomDto.toDomain(): Room {
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
