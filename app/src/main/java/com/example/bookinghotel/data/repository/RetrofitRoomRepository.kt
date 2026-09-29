package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.remote.HotelApiService
import com.example.bookinghotel.data.remote.dto.BookingRequestDto
import com.example.bookinghotel.data.remote.toDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RetrofitRoomRepository @Inject constructor(
    private val api: HotelApiService
) : RoomRepository {

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    override suspend fun refreshRooms(): Result<Unit> {
        return runCatching {
            _rooms.value = api.getRooms().map { it.toDomain() }
        }
    }

    override fun getRoomById(roomId: Int): Room? {
        return _rooms.value.firstOrNull { it.id == roomId }
    }

    override suspend fun bookRoom(roomId: Int, quantity: Int): Result<Room> {
        if (quantity <= 0) {
            return Result.failure(IllegalArgumentException("Quantity must be greater than zero"))
        }

        return runCatching {
            val response = api.createBooking(
                BookingRequestDto(
                    roomId = roomId,
                    quantity = quantity
                )
            )

            val updatedRoom = response.room.toDomain()

            _rooms.value = _rooms.value.map { room ->
                if (room.id == updatedRoom.id) updatedRoom else room
            }

            updatedRoom
        }
    }
}
