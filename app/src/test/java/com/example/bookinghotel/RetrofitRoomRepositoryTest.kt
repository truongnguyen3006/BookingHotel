package com.example.bookinghotel

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.local.BookingEntity
import com.example.bookinghotel.data.local.RoomCacheDao
import com.example.bookinghotel.data.local.RoomCacheEntity
import com.example.bookinghotel.data.remote.HotelApiService
import com.example.bookinghotel.data.remote.dto.BookingRequestDto
import com.example.bookinghotel.data.remote.dto.BookingResponseDto
import com.example.bookinghotel.data.remote.dto.PaymentRequestDto
import com.example.bookinghotel.data.remote.dto.PaymentResponseDto
import com.example.bookinghotel.data.remote.dto.RoomDto
import com.example.bookinghotel.data.repository.RetrofitRoomRepository
import com.example.bookinghotel.data.repository.RoomDataSource
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrofitRoomRepositoryTest {

    @Test
    fun refreshRooms_networkSuccess_mapsAndCachesResponse() = runTest {
        val api = FakeHotelApiService()
        val cacheDao = FakeRoomCacheDao()
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cacheDao)

        val result = repository.refreshRooms()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.rooms.value.size)
        assertEquals("standard", repository.rooms.value.first().typeKey)
        assertEquals(10, repository.rooms.value.first().availableRooms)
        assertEquals(RoomDataSource.NETWORK, repository.roomDataSource.value)
        assertEquals(1, cacheDao.cachedRooms.size)
        assertTrue((repository.lastRoomSyncAt.value ?: 0L) > 0L)
    }

    @Test
    fun refreshRooms_networkFailure_usesCachedRoomsOffline() = runTest {
        val api = FakeHotelApiService().apply {
            roomsFailure = IOException("offline")
        }
        val cacheDao = FakeRoomCacheDao(
            initialRooms = listOf(
                RoomCacheEntity(
                    id = 2,
                    imageKey = "deluxe_room",
                    typeKey = "deluxe",
                    pricePerNight = 80.0,
                    amenities = listOf("Wi-Fi", "TV", "Mini Bar"),
                    availableRooms = 4,
                    lastUpdatedAt = 1234L
                )
            )
        )
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cacheDao)

        val result = repository.refreshRooms()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.rooms.value.size)
        assertEquals("deluxe", repository.rooms.value.first().typeKey)
        assertEquals(4, repository.rooms.value.first().availableRooms)
        assertEquals(RoomDataSource.CACHE, repository.roomDataSource.value)
        assertEquals(1234L, repository.lastRoomSyncAt.value)
    }

    @Test
    fun refreshRooms_networkFailureAndNoCache_returnsFailure() = runTest {
        val api = FakeHotelApiService().apply {
            roomsFailure = IOException("offline")
        }
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), FakeRoomCacheDao())

        val result = repository.refreshRooms()

        assertTrue(result.isFailure)
        assertTrue(repository.rooms.value.isEmpty())
        assertEquals(RoomDataSource.EMPTY, repository.roomDataSource.value)
    }

    @Test
    fun bookRoom_updatesRemoteInventoryAndPersistsBookingAndCache() = runTest {
        val api = FakeHotelApiService()
        val dao = FakeBookingDao(nextInsertedId = 42L)
        val cacheDao = FakeRoomCacheDao()
        val repository = RetrofitRoomRepository(api, dao, cacheDao)
        repository.refreshRooms()

        api.bookingResponse = BookingResponseDto(
            bookingId = 7,
            room = api.roomsResponse.first().copy(availableRooms = 8),
            quantity = 2,
            totalPrice = 100.0,
            status = "PENDING_PAYMENT"
        )

        val booking = repository.bookRoom(roomId = 1, quantity = 2).getOrThrow()

        assertEquals(1, api.createBookingCalls)
        assertEquals(1, api.lastBookingRequest?.roomId)
        assertEquals(2, api.lastBookingRequest?.quantity)
        assertEquals(8, repository.getRoomById(1)?.availableRooms)
        assertEquals(8, cacheDao.cachedRooms.first { it.id == 1 }.availableRooms)
        assertEquals(42L, booking.localId)
        assertEquals(7, booking.bookingId)
        assertEquals("PENDING_PAYMENT", dao.lastInserted?.status)
    }

    @Test
    fun bookRoom_nonPositiveQuantity_failsBeforeCallingApi() = runTest {
        val api = FakeHotelApiService()
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), FakeRoomCacheDao())

        val result = repository.bookRoom(roomId = 1, quantity = 0)

        assertTrue(result.isFailure)
        assertEquals(0, api.createBookingCalls)
    }

    @Test
    fun payBooking_updatesLocalStatusUsingLocalId() = runTest {
        val api = FakeHotelApiService().apply {
            paymentResponse = PaymentResponseDto(
                bookingId = 7,
                status = "SUCCESS",
                method = "QR",
                transactionId = "TXN-7",
                message = "Payment completed"
            )
        }
        val dao = FakeBookingDao()
        val repository = RetrofitRoomRepository(api, dao, FakeRoomCacheDao())
        val booking = Booking(
            localId = 99L,
            bookingId = 7,
            roomId = 1,
            roomTypeKey = "standard",
            quantity = 1,
            pricePerNight = 50.0,
            totalPrice = 50.0,
            status = "PENDING_PAYMENT",
            createdAt = 1L
        )

        val result = repository.payBooking(
            booking = booking,
            method = PaymentMethod.QR,
            simulateFailure = false
        ).getOrThrow()

        assertEquals(7, api.lastPaymentBookingId)
        assertEquals("QR", api.lastPaymentRequest?.method)
        assertFalse(api.lastPaymentRequest?.simulateFailure ?: true)
        assertTrue(api.lastPaymentRequest?.idempotencyKey?.isNotBlank() == true)
        assertEquals(99L, dao.lastUpdatedLocalId)
        assertEquals("SUCCESS", dao.lastUpdatedStatus)
        assertEquals("TXN-7", result.transactionId)
    }

    private class FakeHotelApiService : HotelApiService {
        var roomsResponse: List<RoomDto> = listOf(
            RoomDto(
                id = 1,
                imageKey = "standard_room",
                typeKey = "standard",
                pricePerNight = 50.0,
                amenities = listOf("Wi-Fi", "TV"),
                availableRooms = 10
            )
        )
        var roomsFailure: Throwable? = null

        var bookingResponse: BookingResponseDto = BookingResponseDto(
            bookingId = 1,
            room = roomsResponse.first().copy(availableRooms = 9),
            quantity = 1,
            totalPrice = 50.0,
            status = "PENDING_PAYMENT"
        )

        var paymentResponse: PaymentResponseDto = PaymentResponseDto(
            bookingId = 1,
            status = "SUCCESS",
            method = "CARD",
            transactionId = "TXN-1",
            message = "Payment completed"
        )

        var createBookingCalls = 0
        var lastBookingRequest: BookingRequestDto? = null
        var lastPaymentBookingId: Int? = null
        var lastPaymentRequest: PaymentRequestDto? = null

        override suspend fun getRooms(): List<RoomDto> {
            roomsFailure?.let { throw it }
            return roomsResponse
        }

        override suspend fun createBooking(request: BookingRequestDto): BookingResponseDto {
            createBookingCalls++
            lastBookingRequest = request
            return bookingResponse
        }

        override suspend fun payBooking(
            bookingId: Int,
            request: PaymentRequestDto
        ): PaymentResponseDto {
            lastPaymentBookingId = bookingId
            lastPaymentRequest = request
            return paymentResponse
        }
    }

    private class FakeBookingDao(
        private val nextInsertedId: Long = 1L
    ) : BookingDao {
        private val bookings = MutableStateFlow<List<BookingEntity>>(emptyList())
        var lastInserted: BookingEntity? = null
        var lastUpdatedLocalId: Long? = null
        var lastUpdatedStatus: String? = null

        override fun observeBookings(): Flow<List<BookingEntity>> = bookings

        override suspend fun insertBooking(booking: BookingEntity): Long {
            lastInserted = booking
            val stored = booking.copy(localId = nextInsertedId)
            bookings.value = listOf(stored) + bookings.value
            return nextInsertedId
        }

        override suspend fun updateBookingStatus(localId: Long, status: String) {
            lastUpdatedLocalId = localId
            lastUpdatedStatus = status
            bookings.value = bookings.value.map { entity ->
                if (entity.localId == localId) entity.copy(status = status) else entity
            }
        }

        override suspend fun updatePaymentDetails(
            localId: Long,
            status: String,
            paymentMethod: String?,
            transactionId: String?,
            paidAt: Long?
        ) {
            lastUpdatedLocalId = localId
            lastUpdatedStatus = status
            bookings.value = bookings.value.map { entity ->
                if (entity.localId == localId) {
                    entity.copy(
                        status = status,
                        paymentMethod = paymentMethod,
                        transactionId = transactionId,
                        paidAt = paidAt
                    )
                } else {
                    entity
                }
            }
        }
    }

    private class FakeRoomCacheDao(
        initialRooms: List<RoomCacheEntity> = emptyList()
    ) : RoomCacheDao {
        var cachedRooms: List<RoomCacheEntity> = initialRooms

        override suspend fun getRooms(): List<RoomCacheEntity> = cachedRooms

        override suspend fun getLastUpdatedAt(): Long? =
            cachedRooms.maxOfOrNull { it.lastUpdatedAt }

        override suspend fun upsertRooms(rooms: List<RoomCacheEntity>) {
            rooms.forEach { upsertRoom(it) }
        }

        override suspend fun upsertRoom(room: RoomCacheEntity) {
            cachedRooms = cachedRooms.filterNot { it.id == room.id } + room
        }

        override suspend fun clearRooms() {
            cachedRooms = emptyList()
        }

        override suspend fun replaceAll(rooms: List<RoomCacheEntity>) {
            cachedRooms = rooms
        }
    }
}
