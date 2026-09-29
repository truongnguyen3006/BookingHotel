package com.example.bookinghotel.ui

import androidx.lifecycle.ViewModel
import com.example.bookinghotel.R
import com.example.bookinghotel.data.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
class BookingViewModel : ViewModel(){
    private val _rooms = MutableStateFlow(
        listOf(
            Room(1, R.drawable.standard_room,R.string.room_style_1, 50.0, listOf("Wi-Fi", "TV"), 10),
            Room(2, R.drawable.deluxe_room,R.string.room_style_2, 80.0, listOf("Wi-Fi", "TV", "Mini Bar"), 10),
            Room(3,R.drawable.suite_room, R.string.room_style_3, 120.0, listOf("Wi-Fi", "TV", "Mini Bar", "Jacuzzi"), 10),
            Room(4,R.drawable.executive_room, R.string.room_style_4, 150.0, listOf("Wi-Fi", "TV", "Mini Bar", "Jacuzzi", "Breakfast"), 10),
            Room(5,R.drawable.family_room, R.string.room_style_5, 100.0, listOf("Wi-Fi", "TV", "Kitchenette"), 10)
        )
    )
    val rooms: StateFlow<List<Room>> = _rooms

    private val _selectedRoom = MutableStateFlow<Room?>(null)
    val selectedRoom: StateFlow<Room?> = _selectedRoom

    fun selectRoom(room: Room) {
        _selectedRoom.value = room
    }

    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity

    fun setQuantity(quantity: Int) {
        _quantity.value = quantity
    }

    fun bookRoom(quantity: Int) {
        val currentRoom = _selectedRoom.value ?: return
        val updatedRoom = currentRoom.copy(availableRooms = currentRoom.availableRooms - quantity)
        
        _rooms.value = _rooms.value.map { 
            if (it.id == updatedRoom.id) updatedRoom else it 
        }
        _selectedRoom.value = updatedRoom
    }
}