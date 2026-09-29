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

    override suspend fun bookRoom(roomId: Int, quantity: Int): Result<Booking> {
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

            val booking = Booking(
                bookingId = response.bookingId,
                roomId = updatedRoom.id,
                roomTypeKey = updatedRoom.typeKey,
                quantity = response.quantity,
                pricePerNight = updatedRoom.pricePerNight,
                totalPrice = response.totalPrice,
                status = response.status,
                createdAt = System.currentTimeMillis()
            )

            val localId = bookingDao.insertBooking(booking.toEntity())
            booking.copy(localId = localId)
        }
    }

    override suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean
    ): Result<PaymentResult> {
        return runCatching {
            val response = api.payBooking(
                bookingId = booking.bookingId,
                request = PaymentRequestDto(
                    method = method.name,
                    simulateFailure = simulateFailure
                )
            )

            bookingDao.updateBookingStatus(
                localId = booking.localId,
                status = response.status
            )

            PaymentResult(
                bookingId = response.bookingId,
                status = response.status,
                method = runCatching { PaymentMethod.valueOf(response.method) }
                    .getOrDefault(method),
                transactionId = response.transactionId,
                message = response.message
            )
        }
    }
}
