package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RoomRepository {
    val rooms: StateFlow<List<Room>>
    val bookingHistory: Flow<List<Booking>>

    suspend fun refreshRooms(): Result<Unit>

    fun getRoomById(roomId: Int): Room?

    suspend fun bookRoom(roomId: Int, quantity: Int): Result<Booking>

    suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean = false
    ): Result<PaymentResult>
}
