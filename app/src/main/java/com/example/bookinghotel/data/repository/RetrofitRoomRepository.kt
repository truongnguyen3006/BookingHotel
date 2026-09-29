package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.local.toDomain
import com.example.bookinghotel.data.local.toEntity
import com.example.bookinghotel.data.remote.HotelApiService
import com.example.bookinghotel.data.remote.dto.BookingRequestDto
import com.example.bookinghotel.data.remote.dto.PaymentRequestDto
import com.example.bookinghotel.data.remote.toDomain
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class RetrofitRoomRepository @Inject constructor(
    private val api: HotelApiService,
    private val bookingDao: BookingDao
) : RoomRepository {

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    override val bookingHistory: Flow<List<Booking>> =
        bookingDao.observeBookings().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun refreshRooms(): Result<Unit> {
        return runCatching {
            _rooms.value = api.getRooms().map { it.toDomain() }
        }
    }

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
        if (quantity <= 0) {
            return Result.failure(IllegalArgumentException("Quantity must be greater than zero"))
        }
        if (guests <= 0) {
            return Result.failure(IllegalArgumentException("Guests must be greater than zero"))
        }
        if (checkInDate > 0L && checkOutDate > 0L && checkOutDate <= checkInDate) {
            return Result.failure(IllegalArgumentException("Check-out must be after check-in"))
        }

        val normalizedCheckIn = checkInDate.takeIf { it > 0L } ?: startOfToday()
        val normalizedCheckOut = checkOutDate.takeIf { it > normalizedCheckIn }
            ?: normalizedCheckIn + DAY_MS

        return runCatching {
            val response = api.createBooking(
                BookingRequestDto(
                    roomId = roomId,
                    quantity = quantity,
                    checkInDate = normalizedCheckIn,
                    checkOutDate = normalizedCheckOut,
                    guests = guests
                )
            )

            val updatedRoom = response.room.toDomain()
            _rooms.value = _rooms.value.map { room ->
                if (room.id == updatedRoom.id) updatedRoom else room
            }

            val nights = response.nights.takeIf { it > 0 }
                ?: calculateNights(response.checkInDate, response.checkOutDate)

            val booking = Booking(
                bookingId = response.bookingId,
                roomId = updatedRoom.id,
                roomTypeKey = updatedRoom.typeKey,
                quantity = response.quantity,
                pricePerNight = updatedRoom.pricePerNight,
                totalPrice = response.totalPrice,
                status = response.status,
                createdAt = System.currentTimeMillis(),
                checkInDate = response.checkInDate,
                checkOutDate = response.checkOutDate,
                guests = response.guests,
                nights = nights
            )

            val localId = bookingDao.insertBooking(booking.toEntity())
            booking.copy(localId = localId)
        }
    }

    override suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean,
        idempotencyKey: String
    ): Result<PaymentResult> {
        return runCatching {
            val response = api.payBooking(
                bookingId = booking.bookingId,
                request = PaymentRequestDto(
                    method = method.name,
                    simulateFailure = simulateFailure,
                    idempotencyKey = idempotencyKey.ifBlank { UUID.randomUUID().toString() }
                )
            )

            bookingDao.updatePaymentDetails(
                localId = booking.localId,
                status = response.status,
                paymentMethod = response.method,
                transactionId = response.transactionId,
                paidAt = response.paidAt
            )

            PaymentResult(
                bookingId = response.bookingId,
                status = response.status,
                method = runCatching { PaymentMethod.valueOf(response.method) }
                    .getOrDefault(method),
                transactionId = response.transactionId,
                message = response.message,
                paidAt = response.paidAt
            )
        }
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

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
