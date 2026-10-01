package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.VnPayPaymentSession
import com.example.bookinghotel.data.VnPayPaymentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class RoomDataSource {
    EMPTY,
    NETWORK,
    CACHE,
    MEMORY
}

interface RoomRepository {
    val rooms: StateFlow<List<Room>>
    val bookingHistory: Flow<List<Booking>>
    val roomDataSource: StateFlow<RoomDataSource>
    val lastRoomSyncAt: StateFlow<Long?>

    suspend fun refreshRooms(): Result<Unit>
    suspend fun syncRoomsFromNetwork(): Result<Unit>
    suspend fun refreshBookingHistory(): Result<Unit>

    fun getRoomById(roomId: Int): Room?

    suspend fun bookRoom(
        roomId: Int,
        quantity: Int,
        checkInDate: Long = 0L,
        checkOutDate: Long = 0L,
        guests: Int = 1
    ): Result<Booking>

    suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean = false,
        idempotencyKey: String = ""
    ): Result<PaymentResult>

    suspend fun createVnPayPayment(
        booking: Booking,
        idempotencyKey: String
    ): Result<VnPayPaymentSession>

    suspend fun getVnPayPaymentStatus(
        booking: Booking
    ): Result<VnPayPaymentStatus>
}
