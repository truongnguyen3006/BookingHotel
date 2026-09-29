package com.example.bookinghotel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookingEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BookingDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE bookings ADD COLUMN checkInDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE bookings ADD COLUMN checkOutDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE bookings ADD COLUMN guests INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE bookings ADD COLUMN nights INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE bookings ADD COLUMN paymentMethod TEXT")
                database.execSQL("ALTER TABLE bookings ADD COLUMN transactionId TEXT")
                database.execSQL("ALTER TABLE bookings ADD COLUMN paidAt INTEGER")
            }
        }
    }
}
