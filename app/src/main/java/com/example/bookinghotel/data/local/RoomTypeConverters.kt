package com.example.bookinghotel.data.local

import androidx.room.TypeConverter

class RoomTypeConverters {

    @TypeConverter
    fun fromAmenities(value: List<String>): String {
        return value.joinToString(SEPARATOR)
    }

    @TypeConverter
    fun toAmenities(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(SEPARATOR)
    }

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
