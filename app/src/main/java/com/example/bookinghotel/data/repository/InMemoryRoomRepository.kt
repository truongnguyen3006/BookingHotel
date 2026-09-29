package com.example.bookinghotel.data.repository

import com.example.bookinghotel.R
import com.example.bookinghotel.data.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryRoomRepository : RoomRepository {

    private val _rooms = MutableStateFlow(createInitialRooms())
    override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    override fun getRoomById(roomId: Int): Room? {
        return _rooms.value.firstOrNull { it.id == roomId }
    }

    override fun bookRoom(roomId: Int, quantity: Int): Room? {
        val currentRoom = getRoomById(roomId) ?: return null

        if (quantity <= 0 || quantity > currentRoom.availableRooms) {
            return null
        }

        val updatedRoom = currentRoom.copy(
            availableRooms = currentRoom.availableRooms - quantity
        )

        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) updatedRoom else room
        }

        return updatedRoom
    }

    private fun createInitialRooms(): List<Room> {
        return listOf(
            Room(
                id = 1,
                image = R.drawable.standard_room,
                type = R.string.room_style_1,
                pricePerNight = 50.0,
                amenities = listOf("Wi-Fi", "TV"),
                availableRooms = 10
            ),
            Room(
                id = 2,
                image = R.drawable.deluxe_room,
                type = R.string.room_style_2,
                pricePerNight = 80.0,
                amenities = listOf("Wi-Fi", "TV", "Mini Bar"),
                availableRooms = 10
            ),
            Room(
                id = 3,
                image = R.drawable.suite_room,
                type = R.string.room_style_3,
                pricePerNight = 120.0,
                amenities = listOf("Wi-Fi", "TV", "Mini Bar", "Jacuzzi"),
                availableRooms = 10
            ),
            Room(
                id = 4,
                image = R.drawable.executive_room,
                type = R.string.room_style_4,
                pricePerNight = 150.0,
                amenities = listOf("Wi-Fi", "TV", "Mini Bar", "Jacuzzi", "Breakfast"),
                availableRooms = 10
            ),
            Room(
                id = 5,
                image = R.drawable.family_room,
                type = R.string.room_style_5,
                pricePerNight = 100.0,
                amenities = listOf("Wi-Fi", "TV", "Kitchenette"),
                availableRooms = 10
            )
        )
    }
}
