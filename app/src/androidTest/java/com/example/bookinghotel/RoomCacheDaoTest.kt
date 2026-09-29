package com.example.bookinghotel

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bookinghotel.data.local.BookingDatabase
import com.example.bookinghotel.data.local.RoomCacheEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCacheDaoTest {

    private lateinit var database: BookingDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            BookingDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replaceAll_replacesOldCacheAndPreservesAmenities() = runBlocking {
        val dao = database.roomCacheDao()
        dao.replaceAll(
            listOf(
                cachedRoom(id = 1, availableRooms = 10, updatedAt = 1000L),
                cachedRoom(id = 2, availableRooms = 5, updatedAt = 1000L)
            )
        )

        dao.replaceAll(
            listOf(
                cachedRoom(id = 2, availableRooms = 4, updatedAt = 2000L)
            )
        )

        val rooms = dao.getRooms()
        assertEquals(1, rooms.size)
        assertEquals(2, rooms.single().id)
        assertEquals(4, rooms.single().availableRooms)
        assertEquals(listOf("Wi-Fi", "TV"), rooms.single().amenities)
        assertEquals(2000L, dao.getLastUpdatedAt())
    }

    @Test
    fun upsertRoom_updatesInventoryWithoutDuplicatingRow() = runBlocking {
        val dao = database.roomCacheDao()
        dao.upsertRoom(cachedRoom(id = 1, availableRooms = 10, updatedAt = 1000L))
        dao.upsertRoom(cachedRoom(id = 1, availableRooms = 7, updatedAt = 2000L))

        val rooms = dao.getRooms()
        assertEquals(1, rooms.size)
        assertEquals(7, rooms.single().availableRooms)
        assertEquals(2000L, rooms.single().lastUpdatedAt)
    }

    private fun cachedRoom(
        id: Int,
        availableRooms: Int,
        updatedAt: Long
    ): RoomCacheEntity {
        return RoomCacheEntity(
            id = id,
            imageKey = if (id == 1) "standard_room" else "deluxe_room",
            typeKey = if (id == 1) "standard" else "deluxe",
            pricePerNight = if (id == 1) 50.0 else 80.0,
            amenities = listOf("Wi-Fi", "TV"),
            availableRooms = availableRooms,
            lastUpdatedAt = updatedAt
        )
    }
}
