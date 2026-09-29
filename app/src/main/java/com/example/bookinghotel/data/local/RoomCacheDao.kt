package com.example.bookinghotel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RoomCacheDao {

    @Query("SELECT * FROM room_cache ORDER BY id ASC")
    suspend fun getRooms(): List<RoomCacheEntity>

    @Query("SELECT MAX(lastUpdatedAt) FROM room_cache")
    suspend fun getLastUpdatedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRooms(rooms: List<RoomCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: RoomCacheEntity)

    @Query("DELETE FROM room_cache")
    suspend fun clearRooms()

    @Transaction
    suspend fun replaceAll(rooms: List<RoomCacheEntity>) {
        clearRooms()
        upsertRooms(rooms)
    }
}
