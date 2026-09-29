package com.example.bookinghotel.data.repository

import com.example.bookinghotel.R
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight fake implementation for previews/tests.
 * The running app uses RetrofitRoomRepository.
 */
class InMemoryRoomRepository : RoomRepository {

    private val _rooms = MutableStateFlow(createInitialRooms())
    override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _bookingHistory = MutableStateFlow<List<Booking>>(emptyList())
    override val bookingHistory: StateFlow<List<Booking>> = _bookingHistory.asStateFlow()

    private var nextBookingId = 1
    private var nextLocalId = 1L

    override suspend fun refreshRooms(): Result<Unit> = Result.success(Unit)

    override fun getRoomById(roomId: Int): Room? {
        return _rooms.value.firstOrNull { it.id == roomId }
    }

    override suspend fun bookRoom(roomId: Int, quantity: Int): Result<Booking> {
        val currentRoom = getRoomById(roomId)
            ?: return Result.failure(IllegalArgumentException("Room not found"))

        if (quantity <= 0 || quantity > currentRoom.availableRooms) {
            return Result.failure(IllegalArgumentException("Invalid booking quantity"))
        }

        val updatedRoom = currentRoom.copy(
            availableRooms = currentRoom.availableRooms - quantity
        )

        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) updatedRoom else room
        }

        val booking = Booking(
            localId = nextLocalId++,
            bookingId = nextBookingId++,
            roomId = updatedRoom.id,
            roomTypeKey = updatedRoom.typeKey,
            quantity = quantity,
            pricePerNight = updatedRoom.pricePerNight,
            totalPrice = updatedRoom.pricePerNight * quantity,
            status = "PENDING_PAYMENT",
            createdAt = System.currentTimeMillis()
        )

        _bookingHistory.value = listOf(booking) + _bookingHistory.value
        return Result.success(booking)
    }

    override suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean
    ): Result<PaymentResult> {
        val currentBooking = _bookingHistory.value.firstOrNull { it.localId == booking.localId }
            ?: return Result.failure(IllegalArgumentException("Booking not found"))

        val status = if (simulateFailure) "FAILED" else "SUCCESS"
        val updatedBooking = currentBooking.copy(status = status)
        _bookingHistory.value = _bookingHistory.value.map { item ->
            if (item.localId == booking.localId) updatedBooking else item
        }

        return Result.success(
            PaymentResult(
                bookingId = booking.bookingId,
                status = status,
                method = method,
                transactionId = if (status == "SUCCESS") "FAKE-${booking.bookingId}" else null,
                message = if (status == "SUCCESS") {
                    "Payment completed"
                } else {
                    "Payment failed (simulated)"
                }
            )
        )
    }

    private fun createInitialRooms(): List<Room> {
        return listOf(
            Room(
                id = 1,
                image = R.drawable.standard_room,
                type = R.string.room_style_1,
                typeKey = "standard",
                pricePerNight = 50.0,
                amenities = listOf("Wi-Fi", "TV"),
                availableRooms = 10
            ),
            Room(
                id = 2,
                image = R.drawable.deluxe_room,
                type = R.string.room_style_2,
                typeKey = "deluxe",
                pricePerNight = 80.0,
                amenities = listOf("Wi-Fi", "TV", "Mini Bar"),
                availableRooms = 10
            ),
            Room(
                id = 3,
                image = R.drawable.suite_room,
                type = R.string.room_style_3,
                typeKey = "suite",
                pricePerNight = 120.0,
                amenities = listOf("Wi-Fi", "TV", "Mini Bar", "Jacuzzi"),
                availableRooms = 10
            ),
            Room(
                id = 4,
                image = R.drawable.executive_room,
                type = R.string.room_style_4,
                typeKey = "executive",
                pricePerNight = 150.0,
                amenities = listOf("Wi-Fi", "TV", "Mini Bar", "Jacuzzi", "Breakfast"),
                availableRooms = 10
            ),
            Room(
                id = 5,
                image = R.drawable.family_room,
                type = R.string.room_style_5,
                typeKey = "family",
                pricePerNight = 100.0,
                amenities = listOf("Wi-Fi", "TV", "Kitchenette"),
                availableRooms = 10
            )
        )
    }
}
