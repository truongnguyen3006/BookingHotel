package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.Room
import kotlinx.coroutines.flow.StateFlow

interface RoomRepository {
    val rooms: StateFlow<List<Room>>

    fun getRoomById(roomId: Int): Room?

    fun bookRoom(roomId: Int, quantity: Int): Room?
}
