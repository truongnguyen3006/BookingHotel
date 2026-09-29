package com.example.bookinghotel.data

import com.example.bookinghotel.R

fun roomImageResource(imageKey: String): Int {
    return when (imageKey) {
        "standard_room" -> R.drawable.standard_room
        "deluxe_room" -> R.drawable.deluxe_room
        "suite_room" -> R.drawable.suite_room
        "executive_room" -> R.drawable.executive_room
        "family_room" -> R.drawable.family_room
        else -> R.drawable.standard_room
    }
}

fun roomTypeResource(typeKey: String): Int {
    return when (typeKey) {
        "standard" -> R.string.room_style_1
        "deluxe" -> R.string.room_style_2
        "suite" -> R.string.room_style_3
        "executive" -> R.string.room_style_4
        "family" -> R.string.room_style_5
        else -> R.string.room_style_1
    }
}

fun roomImageKey(typeKey: String): String {
    return when (typeKey) {
        "standard" -> "standard_room"
        "deluxe" -> "deluxe_room"
        "suite" -> "suite_room"
        "executive" -> "executive_room"
        "family" -> "family_room"
        else -> "standard_room"
    }
}
