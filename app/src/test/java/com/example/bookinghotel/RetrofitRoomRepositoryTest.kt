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
import com.example.bookinghotel.data.remote.dto.VnPayCreateRequestDto
import com.example.bookinghotel.data.remote.dto.VnPayCreateResponseDto
import com.example.bookinghotel.data.remote.dto.VnPayStatusResponseDto
import com.example.bookinghotel.data.repository.RetrofitRoomRepository
import com.example.bookinghotel.data.repository.RoomDataSource
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cacheDao, FakeSessionStore())

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
                    pricePerNight = 2_000_000L,
                    amenities = listOf("Wi-Fi", "TV", "Mini Bar"),
                    availableRooms = 4,
                    lastUpdatedAt = 1234L
                )
            )
        )
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cacheDao, FakeSessionStore())

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
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), FakeRoomCacheDao(), FakeSessionStore())

        val result = repository.refreshRooms()

        assertTrue(result.isFailure)
        assertTrue(repository.rooms.value.isEmpty())
        assertEquals(RoomDataSource.EMPTY, repository.roomDataSource.value)
    }

    @Test
    fun syncRoomsFromNetwork_networkFailure_propagatesFailureForWorkManagerRetry() = runTest {
        val api = FakeHotelApiService().apply {
            roomsFailure = IOException("offline")
        }
        val cacheDao = FakeRoomCacheDao(
            initialRooms = listOf(
                RoomCacheEntity(
                    id = 1,
                    imageKey = "standard_room",
                    typeKey = "standard",
                    pricePerNight = 1_250_000L,
                    amenities = listOf("Wi-Fi"),
                    availableRooms = 3,
                    lastUpdatedAt = 1234L
                )
            )
        )
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cacheDao, FakeSessionStore())
        repository.refreshRooms() // publishes cache and hides the remote failure for foreground UX

        val result = repository.syncRoomsFromNetwork()

        assertTrue(result.isFailure)
        assertEquals(3, repository.rooms.value.first().availableRooms)
        assertEquals(RoomDataSource.CACHE, repository.roomDataSource.value)
    }

    @Test
    fun bookRoom_updatesRemoteInventoryAndPersistsBookingAndCache() = runTest {
        val api = FakeHotelApiService()
        val dao = FakeBookingDao(nextInsertedId = 42L)
        val cacheDao = FakeRoomCacheDao()
        val repository = RetrofitRoomRepository(api, dao, cacheDao, FakeSessionStore())
        repository.refreshRooms()

        api.bookingResponse = BookingResponseDto(
            bookingId = 7,
            room = api.roomsResponse.first().copy(availableRooms = 8),
            quantity = 2,
            totalPrice = 2_500_000L,
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
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), FakeRoomCacheDao(), FakeSessionStore())

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
        val repository = RetrofitRoomRepository(api, dao, FakeRoomCacheDao(), FakeSessionStore())
        val booking = Booking(
            localId = 99L,
            bookingId = 7,
            roomId = 1,
            roomTypeKey = "standard",
            quantity = 1,
            pricePerNight = 1_250_000L,
            totalPrice = 1_250_000L,
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

    @Test
    fun createVnPayPayment_updatesLocalBookingToProcessing() = runTest {
        val api = FakeHotelApiService()
        val dao = FakeBookingDao()
        val repository = RetrofitRoomRepository(api, dao, FakeRoomCacheDao(), FakeSessionStore())
        val booking = Booking(
            localId = 77L,
            bookingId = 9,
            roomId = 1,
            roomTypeKey = "standard",
            quantity = 1,
            pricePerNight = 1_250_000L,
            totalPrice = 1_250_000L,
            status = "PENDING_PAYMENT",
            createdAt = 1L
        )

        val session = repository.createVnPayPayment(booking, "idem-vnpay-1").getOrThrow()

        assertEquals(9, session.bookingId)
        assertTrue(session.paymentUrl.startsWith("https://sandbox.vnpayment.vn/"))
        assertEquals(77L, dao.lastUpdatedLocalId)
        assertEquals("PROCESSING", dao.lastUpdatedStatus)
    }

    @Test
    fun historyResponseFromPreviousSession_isDiscardedAfterAnotherUserLogsIn() = runTest {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val api = FakeHotelApiService().apply {
            historyResponse = listOf(bookingResponse)
            beforeHistoryResponse = { started.complete(Unit); release.await() }
        }
        val dao = FakeBookingDao()
        val store = FakeSessionStore().apply { onClear = dao::clearBookings }
        val repository = RetrofitRoomRepository(api, dao, FakeRoomCacheDao(), store)
        val sync = async { repository.refreshBookingHistory() }
        started.await()
        store.clear()
        store.save(testSession(2))
        release.complete(Unit)
        assertTrue(sync.await().isFailure)
        assertTrue(repository.bookingHistory.first().isEmpty())
        assertEquals(null, dao.lastInserted)
    }

    @Test
    fun sameAccountRelogin_alsoDiscardsPreviousSessionResponse() = runTest {
        val store = FakeSessionStore()
        val api = FakeHotelApiService().apply {
            historyResponse = listOf(bookingResponse)
            beforeHistoryResponse = { store.save(testSession(1)) }
        }
        val dao = FakeBookingDao()
        val repository = RetrofitRoomRepository(api, dao, FakeRoomCacheDao(), store)
        assertTrue(repository.refreshBookingHistory().isFailure)
        assertEquals(null, dao.lastInserted)
    }

    @Test
    fun paymentFailureAndRetryAndVnpayPolling_refreshInventoryAutomatically() = runTest {
        val api = FakeHotelApiService()
        val cache = FakeRoomCacheDao()
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cache, FakeSessionStore())
        repository.refreshRooms()
        val booking = repository.bookRoom(1, 1).getOrThrow()
        assertEquals(9, repository.rooms.value.first().availableRooms)
        api.paymentResponse = api.paymentResponse.copy(status = "FAILED")
        repository.payBooking(booking, PaymentMethod.CARD, true).getOrThrow()
        assertEquals(10, repository.rooms.value.first().availableRooms)
        api.roomsResponse = api.roomsResponse.map { it.copy(availableRooms = 9) }
        repository.createVnPayPayment(booking, "retry").getOrThrow()
        assertEquals(9, repository.rooms.value.first().availableRooms)
        api.roomsResponse = api.roomsResponse.map { it.copy(availableRooms = 10) }
        api.vnpayStatus = "FAILED"
        repository.getVnPayPaymentStatus(booking).getOrThrow()
        assertEquals(10, repository.rooms.value.first().availableRooms)
        assertEquals(10, cache.cachedRooms.first().availableRooms)
    }

    @Test
    fun concurrentCatalogRefresh_cannotOverwriteLaterBookingResponse() = runTest {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val api = FakeHotelApiService()
        val cache = FakeRoomCacheDao()
        val repository = RetrofitRoomRepository(api, FakeBookingDao(), cache, FakeSessionStore())
        repository.refreshRooms()
        api.beforeRoomsResponse = { started.complete(Unit); release.await() }
        val refresh = async { repository.syncRoomsFromNetwork() }
        started.await()
        val booking = async { repository.bookRoom(1, 1) }
        release.complete(Unit)
        assertTrue(refresh.await().isSuccess)
        booking.await().getOrThrow()
        assertEquals(9, repository.rooms.value.first().availableRooms)
        assertEquals(9, cache.cachedRooms.first().availableRooms)
    }

    private class FakeHotelApiService : HotelApiService {
        var roomsResponse: List<RoomDto> = listOf(
            RoomDto(
                id = 1,
                imageKey = "standard_room",
                typeKey = "standard",
                pricePerNight = 1_250_000L,
                amenities = listOf("Wi-Fi", "TV"),
                availableRooms = 10
            )
        )
        var beforeHistoryResponse: suspend () -> Unit = {}
        var beforeRoomsResponse: suspend () -> Unit = {}
        var historyResponse: List<BookingResponseDto> = emptyList()
        var vnpayStatus: String = "SUCCESS"
        var roomsFailure: Throwable? = null

        var bookingResponse: BookingResponseDto = BookingResponseDto(
            bookingId = 1,
            room = roomsResponse.first().copy(availableRooms = 9),
            quantity = 1,
            totalPrice = 1_250_000L,
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
            beforeRoomsResponse()
            return roomsResponse
        }

        override suspend fun getBookings(): List<BookingResponseDto> { beforeHistoryResponse(); return historyResponse }

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

        override suspend fun createVnPayPayment(
            bookingId: Int,
            request: VnPayCreateRequestDto
        ): VnPayCreateResponseDto = VnPayCreateResponseDto(
            bookingId = bookingId,
            status = "PENDING",
            paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?demo=1",
            txnRef = "BH${bookingId}123",
            amountVnd = 1_250_000L,
            expiresAt = System.currentTimeMillis() + 900_000L
        )

        override suspend fun getVnPayPaymentStatus(
            bookingId: Int
        ): VnPayStatusResponseDto = VnPayStatusResponseDto(
            bookingId = bookingId,
            status = vnpayStatus,
            method = "VNPAY",
            transactionId = "VNP123",
            txnRef = "BH${bookingId}123",
            amountVnd = 1_250_000L,
            responseCode = "00",
            message = "Payment completed successfully",
            paidAt = System.currentTimeMillis()
        )
    }

    private class FakeBookingDao(
        private val nextInsertedId: Long = 1L
    ) : BookingDao {
        private val bookings = MutableStateFlow<List<BookingEntity>>(emptyList())
        var lastInserted: BookingEntity? = null
        var lastUpdatedLocalId: Long? = null
        var lastUpdatedStatus: String? = null

        override suspend fun getBookingsForSession(ownerSessionId: String) = bookings.value.filter { it.ownerSessionId == ownerSessionId }

        override fun observeBookings(): Flow<List<BookingEntity>> = bookings

        override suspend fun clearBookings() {
            bookings.value = emptyList()
        }

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
