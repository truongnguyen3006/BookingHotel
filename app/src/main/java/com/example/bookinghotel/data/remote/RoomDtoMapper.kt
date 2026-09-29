package com.example.bookinghotel.data.remote

import com.example.bookinghotel.R
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.remote.dto.RoomDto

fun RoomDto.toDomain(): Room {
    return Room(
        id = id,
        image = imageKey.toDrawableResource(),
        type = typeKey.toStringResource(),
        typeKey = typeKey,
        pricePerNight = pricePerNight,
        amenities = amenities,
        availableRooms = availableRooms
    )
}

private fun String.toDrawableResource(): Int {
    return when (this) {
        "standard_room" -> R.drawable.standard_room
        "deluxe_room" -> R.drawable.deluxe_room
        "suite_room" -> R.drawable.suite_room
        "executive_room" -> R.drawable.executive_room
        "family_room" -> R.drawable.family_room
        else -> R.drawable.standard_room
    }
}

private fun String.toStringResource(): Int {
    return when (this) {
        "standard" -> R.string.room_style_1
        "deluxe" -> R.string.room_style_2
        "suite" -> R.string.room_style_3
        "executive" -> R.string.room_style_4
        "family" -> R.string.room_style_5
        else -> R.string.room_style_1
    }
}
