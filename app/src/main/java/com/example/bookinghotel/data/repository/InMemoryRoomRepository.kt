package com.example.bookinghotel.data.repository

import com.example.bookinghotel.R
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
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

    private val _roomDataSource = MutableStateFlow(RoomDataSource.MEMORY)
    override val roomDataSource: StateFlow<RoomDataSource> = _roomDataSource.asStateFlow()

    private val _lastRoomSyncAt = MutableStateFlow<Long?>(System.currentTimeMillis())
    override val lastRoomSyncAt: StateFlow<Long?> = _lastRoomSyncAt.asStateFlow()

    private val _bookingHistory = MutableStateFlow<List<Booking>>(emptyList())
    override val bookingHistory: StateFlow<List<Booking>> = _bookingHistory.asStateFlow()

    private var nextBookingId = 1
    private var nextLocalId = 1L
    private val paymentAttemptResults = mutableMapOf<String, PaymentResult>()

    override suspend fun refreshRooms(): Result<Unit> = Result.success(Unit)

    override suspend fun syncRoomsFromNetwork(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshBookingHistory(): Result<Unit> = Result.success(Unit)

    override fun getRoomById(roomId: Int): Room? {
        return _rooms.value.firstOrNull { it.id == roomId }
    }

    override suspend fun bookRoom(
        roomId: Int,
        quantity: Int,
        checkInDate: Long,
        checkOutDate: Long,
        guests: Int
    ): Result<Booking> {
        val currentRoom = getRoomById(roomId)
            ?: return Result.failure(IllegalArgumentException("Room not found"))

        if (quantity <= 0 || quantity > currentRoom.availableRooms) {
            return Result.failure(IllegalArgumentException("Invalid booking quantity"))
        }
        if (guests <= 0) {
            return Result.failure(IllegalArgumentException("Invalid guest count"))
        }
        if (checkInDate > 0L && checkOutDate > 0L && checkOutDate <= checkInDate) {
            return Result.failure(IllegalArgumentException("Check-out must be after check-in"))
        }

        val normalizedCheckIn = checkInDate.takeIf { it > 0L } ?: startOfToday()
        val normalizedCheckOut = checkOutDate.takeIf { it > normalizedCheckIn }
            ?: normalizedCheckIn + DAY_MS
        val nights = calculateNights(normalizedCheckIn, normalizedCheckOut)

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
            totalPrice = updatedRoom.pricePerNight * quantity * nights,
            status = "PENDING_PAYMENT",
            createdAt = System.currentTimeMillis(),
            checkInDate = normalizedCheckIn,
            checkOutDate = normalizedCheckOut,
            guests = guests,
            nights = nights
        )

        _bookingHistory.value = listOf(booking) + _bookingHistory.value
        return Result.success(booking)
    }

    override suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean,
        idempotencyKey: String
    ): Result<PaymentResult> {
        val currentBooking = _bookingHistory.value.firstOrNull { it.localId == booking.localId }
            ?: return Result.failure(IllegalArgumentException("Booking not found"))

        val effectiveKey = idempotencyKey.ifBlank { UUID.randomUUID().toString() }
        val attemptKey = "${booking.localId}:$effectiveKey"
        paymentAttemptResults[attemptKey]?.let { return Result.success(it) }

        if (currentBooking.status == "SUCCESS") {
            return Result.failure(IllegalStateException("Booking already paid"))
        }

        val status = if (simulateFailure) "FAILED" else "SUCCESS"
        val transactionId = if (status == "SUCCESS") {
            "FAKE-${booking.bookingId}-${UUID.randomUUID().toString().take(8)}"
        } else {
            null
        }
        val paidAt = if (status == "SUCCESS") System.currentTimeMillis() else null

        val updatedBooking = currentBooking.copy(
            status = status,
            paymentMethod = method.name,
            transactionId = transactionId,
            paidAt = paidAt
        )
        _bookingHistory.value = _bookingHistory.value.map { item ->
            if (item.localId == booking.localId) updatedBooking else item
        }

        val result = PaymentResult(
            bookingId = booking.bookingId,
            status = status,
            method = method,
            transactionId = transactionId,
            message = if (status == "SUCCESS") {
                "Payment completed"
            } else {
                "Payment failed (simulated)"
            },
            paidAt = paidAt
        )
        paymentAttemptResults[attemptKey] = result
        return Result.success(result)
    }

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun calculateNights(checkInDate: Long, checkOutDate: Long): Int {
        return TimeUnit.MILLISECONDS.toDays(checkOutDate - checkInDate)
            .toInt()
            .coerceAtLeast(1)
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

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
