package com.example.bookinghotel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookingEntity::class, RoomCacheEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class BookingDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
    abstract fun roomCacheDao(): RoomCacheDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Older private rows have no provable owner. Refetch after login.
                database.execSQL("DELETE FROM bookings")
                database.execSQL("ALTER TABLE bookings ADD COLUMN ownerSessionId TEXT NOT NULL DEFAULT ''")
            }
        }

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE bookings_v4 (
                        localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteBookingId INTEGER NOT NULL,
                        roomId INTEGER NOT NULL,
                        roomTypeKey TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        pricePerNight INTEGER NOT NULL,
                        totalPrice INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        checkInDate INTEGER NOT NULL DEFAULT 0,
                        checkOutDate INTEGER NOT NULL DEFAULT 0,
                        guests INTEGER NOT NULL DEFAULT 1,
                        nights INTEGER NOT NULL DEFAULT 1,
                        paymentMethod TEXT,
                        transactionId TEXT,
                        paidAt INTEGER
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO bookings_v4 (
                        localId, remoteBookingId, roomId, roomTypeKey, quantity,
                        pricePerNight, totalPrice, status, createdAt, checkInDate,
                        checkOutDate, guests, nights, paymentMethod, transactionId, paidAt
                    )
                    SELECT
                        localId, remoteBookingId, roomId, roomTypeKey, quantity,
                        CASE
                            WHEN pricePerNight < 100000 THEN CAST(ROUND(pricePerNight * 25000) AS INTEGER)
                            ELSE CAST(ROUND(pricePerNight) AS INTEGER)
                        END,
                        CASE
                            WHEN pricePerNight < 100000 THEN CAST(ROUND(totalPrice * 25000) AS INTEGER)
                            ELSE CAST(ROUND(totalPrice) AS INTEGER)
                        END,
                        status, createdAt, checkInDate, checkOutDate, guests, nights,
                        paymentMethod, transactionId, paidAt
                    FROM bookings
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE bookings")
                database.execSQL("ALTER TABLE bookings_v4 RENAME TO bookings")

                database.execSQL(
                    """
                    CREATE TABLE room_cache_v4 (
                        id INTEGER NOT NULL,
                        imageKey TEXT NOT NULL,
                        typeKey TEXT NOT NULL,
                        pricePerNight INTEGER NOT NULL,
                        amenities TEXT NOT NULL,
                        availableRooms INTEGER NOT NULL,
                        lastUpdatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO room_cache_v4 (
                        id, imageKey, typeKey, pricePerNight, amenities, availableRooms, lastUpdatedAt
                    )
                    SELECT
                        id, imageKey, typeKey,
                        CASE
                            WHEN pricePerNight < 100000 THEN CAST(ROUND(pricePerNight * 25000) AS INTEGER)
                            ELSE CAST(ROUND(pricePerNight) AS INTEGER)
                        END,
                        amenities, availableRooms, lastUpdatedAt
                    FROM room_cache
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE room_cache")
                database.execSQL("ALTER TABLE room_cache_v4 RENAME TO room_cache")
            }
        }
    }
}
