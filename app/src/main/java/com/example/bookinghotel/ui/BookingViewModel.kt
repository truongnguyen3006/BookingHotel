package com.example.bookinghotel.ui

import androidx.lifecycle.ViewModel
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.repository.InMemoryRoomRepository
import com.example.bookinghotel.data.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BookingViewModel(
    private val roomRepository: RoomRepository = InMemoryRoomRepository()
) : ViewModel() {

    val rooms: StateFlow<List<Room>> = roomRepository.rooms

    private val _selectedRoom = MutableStateFlow<Room?>(null)
    val selectedRoom: StateFlow<Room?> = _selectedRoom.asStateFlow()

    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity.asStateFlow()

    fun selectRoom(room: Room) {
        _selectedRoom.value = roomRepository.getRoomById(room.id) ?: room
    }

    fun setQuantity(quantity: Int) {
        _quantity.value = quantity
    }

    fun bookRoom(quantity: Int) {
        val currentRoom = _selectedRoom.value ?: return
        val updatedRoom = roomRepository.bookRoom(currentRoom.id, quantity) ?: return

        _selectedRoom.value = updatedRoom
    }
}
