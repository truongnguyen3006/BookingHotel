package com.example.bookinghotel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BookingDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
}
