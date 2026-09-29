package com.example.bookinghotel

import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.repository.InMemoryRoomRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryRoomRepositoryTest {

    @Test
    fun bookRoom_validQuantity_reducesAvailabilityAndAddsHistory() = runTest {
        val repository = InMemoryRoomRepository()

        val result = repository.bookRoom(roomId = 1, quantity = 2)

        assertTrue(result.isSuccess)
        assertEquals(8, repository.getRoomById(1)?.availableRooms)

        val booking = result.getOrThrow()
        assertEquals(2, booking.quantity)
        assertEquals(100.0, booking.totalPrice, 0.0)
        assertEquals("PENDING_PAYMENT", booking.status)

        val history = repository.bookingHistory.first()
        assertEquals(1, history.size)
        assertEquals(booking.localId, history.first().localId)
    }

    @Test
    fun bookRoom_invalidQuantity_doesNotChangeInventory() = runTest {
        val repository = InMemoryRoomRepository()

        val zeroResult = repository.bookRoom(roomId = 1, quantity = 0)
        val negativeResult = repository.bookRoom(roomId = 1, quantity = -1)
        val tooManyResult = repository.bookRoom(roomId = 1, quantity = 11)

        assertTrue(zeroResult.isFailure)
        assertTrue(negativeResult.isFailure)
        assertTrue(tooManyResult.isFailure)
        assertEquals(10, repository.getRoomById(1)?.availableRooms)
        assertTrue(repository.bookingHistory.first().isEmpty())
    }

    @Test
    fun payBooking_simulatedFailure_marksBookingFailed() = runTest {
        val repository = InMemoryRoomRepository()
        val booking = repository.bookRoom(roomId = 1, quantity = 1).getOrThrow()

        val result = repository.payBooking(
            booking = booking,
            method = PaymentMethod.CARD,
            simulateFailure = true
        ).getOrThrow()

        assertEquals("FAILED", result.status)
        assertNull(result.transactionId)
        assertEquals("FAILED", repository.bookingHistory.first().first().status)
    }

    @Test
    fun payBooking_retryAfterFailure_marksSameBookingSuccessful() = runTest {
        val repository = InMemoryRoomRepository()
        val booking = repository.bookRoom(roomId = 1, quantity = 1).getOrThrow()

        repository.payBooking(
            booking = booking,
            method = PaymentMethod.CARD,
            simulateFailure = true
        ).getOrThrow()

        val retry = repository.payBooking(
            booking = booking,
            method = PaymentMethod.QR,
            simulateFailure = false
        ).getOrThrow()

        assertEquals("SUCCESS", retry.status)
        assertEquals(PaymentMethod.QR, retry.method)
        assertNotNull(retry.transactionId)
        assertEquals("SUCCESS", repository.bookingHistory.first().first().status)
        assertEquals(1, repository.bookingHistory.first().size)
    }

    @Test
    fun payBooking_sameIdempotencyKey_returnsSamePaymentResult() = runTest {
        val repository = InMemoryRoomRepository()
        val booking = repository.bookRoom(roomId = 1, quantity = 1).getOrThrow()

        val first = repository.payBooking(
            booking = booking,
            method = PaymentMethod.CARD,
            simulateFailure = false,
            idempotencyKey = "same-key"
        ).getOrThrow()

        val repeated = repository.payBooking(
            booking = booking,
            method = PaymentMethod.QR,
            simulateFailure = true,
            idempotencyKey = "same-key"
        ).getOrThrow()

        assertEquals(first, repeated)
        assertEquals("SUCCESS", repeated.status)
    }

    @Test
    fun bookRoom_multipleNights_calculatesStayTotal() = runTest {
        val repository = InMemoryRoomRepository()
        val checkIn = 1_790_726_400_000L
        val checkOut = checkIn + (3L * 24L * 60L * 60L * 1000L)

        val booking = repository.bookRoom(
            roomId = 1,
            quantity = 2,
            checkInDate = checkIn,
            checkOutDate = checkOut,
            guests = 3
        ).getOrThrow()

        assertEquals(3, booking.nights)
        assertEquals(3, booking.guests)
        assertEquals(300.0, booking.totalPrice, 0.0)
    }
}
