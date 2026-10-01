package com.example.bookinghotel.ui

import com.example.bookinghotel.data.remote.toAppError
import com.example.bookinghotel.data.remote.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.repository.RoomDataSource
import com.example.bookinghotel.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BookingUiState {
    data object Idle : BookingUiState
    data object Loading : BookingUiState
    data object Success : BookingUiState
    data class Error(val message: String) : BookingUiState
}

sealed interface PaymentUiState {
    data object Idle : PaymentUiState
    data object Loading : PaymentUiState
    data class Success(val result: PaymentResult) : PaymentUiState
    data class Failed(val result: PaymentResult) : PaymentUiState
    data class Error(val message: String) : PaymentUiState
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val roomRepository: RoomRepository
) : ViewModel() {

    val rooms: StateFlow<List<Room>> = roomRepository.rooms
    val roomDataSource: StateFlow<RoomDataSource> = roomRepository.roomDataSource
    val lastRoomSyncAt: StateFlow<Long?> = roomRepository.lastRoomSyncAt

    private val _filterState = MutableStateFlow(RoomFilterState())
    val filterState: StateFlow<RoomFilterState> = _filterState.asStateFlow()

    val filteredRooms: StateFlow<List<Room>> = combine(rooms, _filterState) { source, filter ->
        source
            .asSequence()
            .filter { room ->
                val query = filter.query.trim()
                query.isBlank() ||
                    room.typeKey.contains(query, ignoreCase = true) ||
                    room.amenities.any { it.contains(query, ignoreCase = true) }
            }
            .filter { room -> !filter.onlyAvailable || room.availableRooms > 0 }
            .filter { room ->
                when (filter.priceFilter) {
                    PriceFilter.ALL -> true
                    PriceFilter.UNDER_100 -> room.pricePerNight < 100.0
                    PriceFilter.AT_LEAST_100 -> room.pricePerNight >= 100.0
                }
            }
            .filter { room ->
                filter.amenity == null || room.amenities.any {
                    it.equals(filter.amenity, ignoreCase = true)
                }
            }
            .let { sequence ->
                when (filter.sortOption) {
                    RoomSortOption.DEFAULT -> sequence.toList()
                    RoomSortOption.PRICE_LOW_TO_HIGH -> sequence.sortedBy { it.pricePerNight }.toList()
                    RoomSortOption.PRICE_HIGH_TO_LOW -> sequence.sortedByDescending { it.pricePerNight }.toList()
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val bookingHistory: StateFlow<List<Booking>> = roomRepository.bookingHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedRoom = MutableStateFlow<Room?>(null)
    val selectedRoom: StateFlow<Room?> = _selectedRoom.asStateFlow()

    private val _quantity = MutableStateFlow(1)
    val quantity: StateFlow<Int> = _quantity.asStateFlow()

    private val _lastBooking = MutableStateFlow<Booking?>(null)
    val lastBooking: StateFlow<Booking?> = _lastBooking.asStateFlow()

    private val _isLoadingRooms = MutableStateFlow(false)
    val isLoadingRooms: StateFlow<Boolean> = _isLoadingRooms.asStateFlow()

    private val _roomLoadError = MutableStateFlow<String?>(null)
    val roomLoadError: StateFlow<String?> = _roomLoadError.asStateFlow()

    private val _bookingState = MutableStateFlow<BookingUiState>(BookingUiState.Idle)
    val bookingState: StateFlow<BookingUiState> = _bookingState.asStateFlow()

    private val _paymentState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val paymentState: StateFlow<PaymentUiState> = _paymentState.asStateFlow()

    private var paymentIdempotencyKey: String = UUID.randomUUID().toString()

    init {
        loadRooms()
    }

    fun loadRooms() {
        viewModelScope.launch {
            _isLoadingRooms.value = true
            _roomLoadError.value = null

            roomRepository.refreshRooms()
                .onFailure { throwable ->
                    _roomLoadError.value = throwable.toUserMessage()
                }

            _isLoadingRooms.value = false
        }
    }

    fun refreshBookingHistory() {
        viewModelScope.launch {
            roomRepository.refreshBookingHistory()
                .onFailure { throwable ->
                    _roomLoadError.value = throwable.toAppError().userMessage()
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(query = query)
    }

    fun toggleAvailableOnly() {
        _filterState.value = _filterState.value.copy(
            onlyAvailable = !_filterState.value.onlyAvailable
        )
    }

    fun setPriceFilter(filter: PriceFilter) {
        _filterState.value = _filterState.value.copy(priceFilter = filter)
    }

    fun setAmenityFilter(amenity: String?) {
        _filterState.value = _filterState.value.copy(amenity = amenity)
    }

    fun setSortOption(sortOption: RoomSortOption) {
        _filterState.value = _filterState.value.copy(sortOption = sortOption)
    }

    fun clearRoomFilters() {
        _filterState.value = RoomFilterState()
    }

    fun selectRoom(room: Room) {
        _selectedRoom.value = roomRepository.getRoomById(room.id) ?: room
        _bookingState.value = BookingUiState.Idle
    }

    fun bookRoom(
        quantity: Int,
        checkInDate: Long = 0L,
        checkOutDate: Long = 0L,
        guests: Int = 1
    ) {
        val currentRoom = _selectedRoom.value ?: return
        if (checkInDate > 0L && checkOutDate > 0L && checkOutDate <= checkInDate) {
            _bookingState.value = BookingUiState.Error("Ngày trả phòng phải sau ngày nhận phòng.")
            return
        }
        val normalizedCheckIn = checkInDate.takeIf { it > 0L } ?: startOfToday()
        val normalizedCheckOut = checkOutDate.takeIf { it > normalizedCheckIn }
            ?: normalizedCheckIn + DAY_MS

        when {
            quantity <= 0 || quantity > currentRoom.availableRooms -> {
                _bookingState.value = BookingUiState.Error("Số lượng phòng không hợp lệ.")
                return
            }
            guests <= 0 -> {
                _bookingState.value = BookingUiState.Error("Số khách tối thiểu là 1.")
                return
            }
            normalizedCheckOut <= normalizedCheckIn -> {
                _bookingState.value = BookingUiState.Error("Ngày trả phòng phải sau ngày nhận phòng.")
                return
            }
        }

        viewModelScope.launch {
            _bookingState.value = BookingUiState.Loading

            roomRepository.bookRoom(
                roomId = currentRoom.id,
                quantity = quantity,
                checkInDate = normalizedCheckIn,
                checkOutDate = normalizedCheckOut,
                guests = guests
            )
                .onSuccess { booking ->
                    _selectedRoom.value = roomRepository.getRoomById(booking.roomId)
                    _quantity.value = booking.quantity
                    _lastBooking.value = booking
                    paymentIdempotencyKey = UUID.randomUUID().toString()
                    _paymentState.value = PaymentUiState.Idle
                    _bookingState.value = BookingUiState.Success
                }
                .onFailure { throwable ->
                    _bookingState.value = BookingUiState.Error(throwable.toUserMessage())
                }
        }
    }

    fun payBooking(
        method: PaymentMethod,
        simulateFailure: Boolean
    ) {
        val booking = _lastBooking.value
        if (booking == null) {
            _paymentState.value = PaymentUiState.Error("Không tìm thấy booking để thanh toán.")
            return
        }

        viewModelScope.launch {
            _paymentState.value = PaymentUiState.Loading

            roomRepository.payBooking(
                booking = booking,
                method = method,
                simulateFailure = simulateFailure,
                idempotencyKey = paymentIdempotencyKey
            )
                .onSuccess { result ->
                    _lastBooking.value = booking.copy(
                        status = result.status,
                        paymentMethod = result.method.name,
                        transactionId = result.transactionId,
                        paidAt = result.paidAt
                    )
                    _paymentState.value = if (result.status == "SUCCESS") {
                        PaymentUiState.Success(result)
                    } else {
                        // A failed payment is a completed attempt, so the next retry gets a new key.
                        paymentIdempotencyKey = UUID.randomUUID().toString()
                        PaymentUiState.Failed(result)
                    }
                }
                .onFailure { throwable ->
                    // Keep the same key after a network error. Retrying is safe and idempotent.
                    _paymentState.value = PaymentUiState.Error(throwable.toPaymentUserMessage())
                }
        }
    }

    fun preparePayment() {
        paymentIdempotencyKey = UUID.randomUUID().toString()
        _paymentState.value = PaymentUiState.Idle
    }

    fun consumeBookingSuccess() {
        if (_bookingState.value is BookingUiState.Success) {
            _bookingState.value = BookingUiState.Idle
        }
    }

    fun estimatedTotal(
        pricePerNight: Double,
        quantity: Int,
        checkInDate: Long,
        checkOutDate: Long
    ): Double {
        if (quantity <= 0 || checkOutDate <= checkInDate) return 0.0
        return pricePerNight * quantity * calculateNights(checkInDate, checkOutDate)
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

    private fun Throwable.toUserMessage(): String = toAppError().userMessage()

    private fun Throwable.toPaymentUserMessage(): String = toAppError().userMessage()

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
