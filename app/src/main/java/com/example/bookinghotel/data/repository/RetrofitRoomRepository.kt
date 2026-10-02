package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.auth.SessionStore
import com.example.bookinghotel.data.auth.StaleSessionException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.VnPayPaymentSession
import com.example.bookinghotel.data.VnPayPaymentStatus
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.local.RoomCacheDao
import com.example.bookinghotel.data.local.toCacheEntity
import com.example.bookinghotel.data.local.toDomain
import com.example.bookinghotel.data.local.toEntity
import com.example.bookinghotel.data.remote.HotelApiService
import com.example.bookinghotel.data.remote.dto.BookingRequestDto
import com.example.bookinghotel.data.remote.dto.PaymentRequestDto
import com.example.bookinghotel.data.remote.dto.VnPayCreateRequestDto
import com.example.bookinghotel.data.remote.toDomain
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RetrofitRoomRepository @Inject constructor(
    private val api: HotelApiService,
    private val bookingDao: BookingDao,
    private val roomCacheDao: RoomCacheDao,
    private val sessionStore: SessionStore
) : RoomRepository {

    private val catalogMutex = Mutex()
    private val privateDataMutex = Mutex()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _roomDataSource = MutableStateFlow(RoomDataSource.EMPTY)
    override val roomDataSource: StateFlow<RoomDataSource> = _roomDataSource.asStateFlow()

    private val _lastRoomSyncAt = MutableStateFlow<Long?>(null)
    override val lastRoomSyncAt: StateFlow<Long?> = _lastRoomSyncAt.asStateFlow()

    override val bookingHistory: Flow<List<Booking>> =
        combine(sessionStore.session, bookingDao.observeBookings()) { session, entities ->
            entities.filter { session != null && it.ownerSessionId == session.sessionId }
                .map { it.toDomain() }
        }

    /**
     * Offline-first refresh strategy:
     * 1. Load cached rooms immediately when available.
     * 2. Try to refresh from the remote API.
     * 3. On remote success, replace the cache and publish fresh data.
     * 4. On remote failure, keep cached data and mark the source as CACHE.
     *    The failure is returned only when there is no cached data to fall back to.
     */
    override suspend fun refreshRooms(): Result<Unit> = catalogMutex.withLock {
        val cachedRooms = runCatching {
            roomCacheDao.getRooms()
        }.getOrDefault(emptyList())

        if (_rooms.value.isEmpty() && cachedRooms.isNotEmpty()) {
            _rooms.value = cachedRooms.map { it.toDomain() }
            _roomDataSource.value = RoomDataSource.CACHE
            _lastRoomSyncAt.value = runCatching {
                roomCacheDao.getLastUpdatedAt()
            }.getOrNull()
        }

        syncRoomsLocked().recoverCatching { throwable ->
            if (_rooms.value.isNotEmpty()) {
                // Cached content remains usable while the device/server is offline.
                Unit
            } else {
                throw throwable
            }
        }
    }

    /**
     * Performs a real remote refresh and always propagates network/server failures.
     * WorkManager uses this method so failed sync attempts can be retried instead of
     * being hidden by the offline cache fallback used by refreshRooms().
     */
    override suspend fun syncRoomsFromNetwork(): Result<Unit> = catalogMutex.withLock { syncRoomsLocked() }

    private suspend fun syncRoomsLocked(): Result<Unit> = runCatching {
        val freshRooms = api.getRooms().map { it.toDomain() }
        val syncedAt = System.currentTimeMillis()

        roomCacheDao.replaceAll(
            freshRooms.map { room -> room.toCacheEntity(syncedAt) }
        )

        _rooms.value = freshRooms
        _roomDataSource.value = RoomDataSource.NETWORK
        _lastRoomSyncAt.value = syncedAt
    }.onFailure { _roomDataSource.value = if (_rooms.value.isEmpty()) RoomDataSource.EMPTY else RoomDataSource.CACHE }
        .propagateCancellation()

    override suspend fun refreshBookingHistory(): Result<Unit> = authenticatedRun { session ->
        val responses = api.getBookings()
        sessionStore.withSession(session) {
            bookingDao.replaceForSession(session.sessionId, responses.map { response ->
                val room = response.room.toDomain()
                Booking(
                    bookingId = response.bookingId, roomId = room.id, roomTypeKey = room.typeKey,
                    quantity = response.quantity, pricePerNight = room.pricePerNight,
                    totalPrice = response.totalPrice, status = response.status,
                    createdAt = response.createdAt, checkInDate = response.checkInDate,
                    checkOutDate = response.checkOutDate, guests = response.guests, nights = response.nights
                ).toEntity(session.sessionId)
            })
        }
        // Expiration/IPN can change stock without a foreground payment action.
        syncRoomsFromNetwork()
        Unit
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

        return authenticatedRun { session -> catalogMutex.withLock {
            val response = api.createBooking(
                BookingRequestDto(
                    roomId = roomId,
                    quantity = quantity,
                    checkInDate = normalizedCheckIn,
                    checkOutDate = normalizedCheckOut,
                    guests = guests
                )
            )

            sessionStore.withSession(session) {
            val updatedRoom = response.room.toDomain()
            _rooms.value = _rooms.value.map { room ->
                if (room.id == updatedRoom.id) updatedRoom else room
            }

            val syncedAt = System.currentTimeMillis()
            roomCacheDao.upsertRoom(updatedRoom.toCacheEntity(syncedAt))
            _roomDataSource.value = RoomDataSource.NETWORK
            _lastRoomSyncAt.value = syncedAt

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
                createdAt = response.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                checkInDate = response.checkInDate,
                checkOutDate = response.checkOutDate,
                guests = response.guests,
                nights = nights
            )

            val localId = bookingDao.insertBooking(booking.toEntity(session.sessionId))
            booking.copy(localId = localId)
            }
        } }
    }

    override suspend fun payBooking(
        booking: Booking,
        method: PaymentMethod,
        simulateFailure: Boolean,
        idempotencyKey: String
    ): Result<PaymentResult> {
        return authenticatedRun { session -> catalogMutex.withLock {
            val response = api.payBooking(
                bookingId = booking.bookingId,
                request = PaymentRequestDto(
                    method = method.name,
                    simulateFailure = simulateFailure,
                    idempotencyKey = idempotencyKey.ifBlank { UUID.randomUUID().toString() }
                )
            )

            sessionStore.withSession(session) {
            bookingDao.updatePaymentDetails(
                localId = booking.localId,
                status = response.status,
                paymentMethod = response.method,
                transactionId = response.transactionId,
                paidAt = response.paidAt
            )

            }
            syncRoomsLocked() // Payment response remains successful if catalog refresh is offline.
            PaymentResult(
                bookingId = response.bookingId,
                status = response.status,
                method = runCatching { PaymentMethod.valueOf(response.method) }
                    .getOrDefault(method),
                transactionId = response.transactionId,
                message = response.message,
                paidAt = response.paidAt
            )
        } }
    }


    override suspend fun createVnPayPayment(
        booking: Booking,
        idempotencyKey: String
    ): Result<VnPayPaymentSession> = authenticatedRun { session -> catalogMutex.withLock {
        val response = api.createVnPayPayment(
            bookingId = booking.bookingId,
            request = VnPayCreateRequestDto(
                idempotencyKey = idempotencyKey.ifBlank { UUID.randomUUID().toString() }
            )
        )

        sessionStore.withSession(session) {
            // An expired URL during confirmation grace is still PROCESSING, not FAILED.
            val status = when (response.status) {
                "SUCCESS" -> "SUCCESS"
                "FAILED" -> "FAILED"
                else -> "PROCESSING"
            }
            bookingDao.updatePaymentDetails(booking.localId, status, PaymentMethod.VNPAY.name, null, null)
        }
        syncRoomsLocked()
        if (response.status != "PENDING" || response.paymentUrl.isBlank()) {
            throw IllegalStateException("VNPAY payment session is closed. Check its backend status before retrying.")
        }

        VnPayPaymentSession(
            bookingId = response.bookingId,
            paymentUrl = response.paymentUrl,
            txnRef = response.txnRef,
            amountVnd = response.amountVnd,
            expiresAt = response.expiresAt
        )
    } }

    override suspend fun getVnPayPaymentStatus(
        booking: Booking
    ): Result<VnPayPaymentStatus> = authenticatedRun { session -> catalogMutex.withLock {
        val response = api.getVnPayPaymentStatus(booking.bookingId)
        val bookingStatus = when (response.status) {
            "SUCCESS" -> "SUCCESS"
            "FAILED" -> "FAILED"
            else -> "PROCESSING"
        }

        sessionStore.withSession(session) {
        bookingDao.updatePaymentDetails(
            localId = booking.localId,
            status = bookingStatus,
            paymentMethod = PaymentMethod.VNPAY.name,
            transactionId = response.transactionId,
            paidAt = response.paidAt
        )

        }
        syncRoomsLocked()

        VnPayPaymentStatus(
            bookingId = response.bookingId,
            status = response.status,
            transactionId = response.transactionId,
            txnRef = response.txnRef,
            amountVnd = response.amountVnd,
            responseCode = response.responseCode,
            message = response.message,
            paidAt = response.paidAt
        )
    } }

    private suspend fun <T> authenticatedRun(block: suspend (AuthSession) -> T): Result<T> {
        val expected = sessionStore.currentSession() ?: return Result.failure(StaleSessionException())
        return runCatching {
            privateDataMutex.withLock {
                sessionStore.withSession(expected) { Unit }
                block(expected)
            }
        }.propagateCancellation()
    }

    private fun <T> Result<T>.propagateCancellation(): Result<T> = onFailure {
        if (it is CancellationException) throw it
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
