package com.example.bookinghotel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookingEntity::class, RoomCacheEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class BookingDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
    abstract fun roomCacheDao(): RoomCacheDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS room_cache (
                        id INTEGER NOT NULL,
                        imageKey TEXT NOT NULL,
                        typeKey TEXT NOT NULL,
                        pricePerNight REAL NOT NULL,
                        amenities TEXT NOT NULL,
                        availableRooms INTEGER NOT NULL,
                        lastUpdatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
