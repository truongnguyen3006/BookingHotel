package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.local.RoomCacheDao
import com.example.bookinghotel.data.local.toCacheEntity
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
    private val bookingDao: BookingDao,
    private val roomCacheDao: RoomCacheDao
) : RoomRepository {

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _roomDataSource = MutableStateFlow(RoomDataSource.EMPTY)
    override val roomDataSource: StateFlow<RoomDataSource> = _roomDataSource.asStateFlow()

    private val _lastRoomSyncAt = MutableStateFlow<Long?>(null)
    override val lastRoomSyncAt: StateFlow<Long?> = _lastRoomSyncAt.asStateFlow()

    override val bookingHistory: Flow<List<Booking>> =
        bookingDao.observeBookings().map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Offline-first refresh strategy:
     * 1. Load cached rooms immediately when available.
     * 2. Try to refresh from the remote API.
     * 3. On remote success, replace the cache and publish fresh data.
     * 4. On remote failure, keep cached data and consider the refresh usable.
     *    The failure is returned only when there is no cached data to fall back to.
     */
    override suspend fun refreshRooms(): Result<Unit> {
        val cachedRooms = runCatching {
            roomCacheDao.getRooms()
        }.getOrDefault(emptyList())

        if (cachedRooms.isNotEmpty()) {
            _rooms.value = cachedRooms.map { it.toDomain() }
            _roomDataSource.value = RoomDataSource.CACHE
            _lastRoomSyncAt.value = runCatching {
                roomCacheDao.getLastUpdatedAt()
            }.getOrNull()
        }

        return syncRoomsFromNetwork().recoverCatching { throwable ->
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
    override suspend fun syncRoomsFromNetwork(): Result<Unit> = runCatching {
        val freshRooms = api.getRooms().map { it.toDomain() }
        val syncedAt = System.currentTimeMillis()

        roomCacheDao.replaceAll(
            freshRooms.map { room -> room.toCacheEntity(syncedAt) }
        )

        _rooms.value = freshRooms
        _roomDataSource.value = RoomDataSource.NETWORK
        _lastRoomSyncAt.value = syncedAt
    }

    override suspend fun refreshBookingHistory(): Result<Unit> = runCatching {
        val responses = api.getBookings()
        bookingDao.clearBookings()
        responses.forEach { response ->
            val room = response.room.toDomain()
            val booking = Booking(
                bookingId = response.bookingId,
                roomId = room.id,
                roomTypeKey = room.typeKey,
                quantity = response.quantity,
                pricePerNight = room.pricePerNight,
                totalPrice = response.totalPrice,
                status = response.status,
                createdAt = response.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                checkInDate = response.checkInDate,
                checkOutDate = response.checkOutDate,
                guests = response.guests,
                nights = response.nights
            )
            bookingDao.insertBooking(booking.toEntity())
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
