package com.example.bookinghotel.ui

import com.example.bookinghotel.ui.AppStrings
import com.example.bookinghotel.R
import com.example.bookinghotel.data.remote.toAppError
import com.example.bookinghotel.data.remote.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.VnPayPaymentSession
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
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
    data class VnPayReady(val session: VnPayPaymentSession) : PaymentUiState
    data class VnPayPending(val session: VnPayPaymentSession?, val message: String) : PaymentUiState
    data class Success(val result: PaymentResult) : PaymentUiState
    data class Failed(val result: PaymentResult) : PaymentUiState
    data class Error(val message: String) : PaymentUiState
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val roomRepository: RoomRepository,
    private val strings: AppStrings
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
                    PriceFilter.UNDER_2_5_MILLION -> room.pricePerNight < 2_500_000L
                    PriceFilter.AT_LEAST_2_5_MILLION -> room.pricePerNight >= 2_500_000L
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
        viewModelScope.launch {
            rooms.collect { latest ->
                val selected = _selectedRoom.value
                if (selected != null) _selectedRoom.value = latest.firstOrNull { it.id == selected.id } ?: selected
            }
        }
    }

    fun loadRooms() {
        if (_isLoadingRooms.value) return
        _isLoadingRooms.value = true
        viewModelScope.launch {
            _roomLoadError.value = null

            roomRepository.refreshRooms()
                .onFailure { throwable ->
                    _roomLoadError.value = throwable.toUserMessage()
                }

            _isLoadingRooms.value = false
        }
    }

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading = _historyLoading.asStateFlow()
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError = _historyError.asStateFlow()
    private var checkingPayment = false

    fun refreshBookingHistory() {
        if (_historyLoading.value) return
        _historyLoading.value = true
        _historyError.value = null
        viewModelScope.launch {
            roomRepository.refreshBookingHistory()
                .onSuccess {
                    val current = _lastBooking.value
                    if (current != null) {
                        val latest = roomRepository.bookingHistory.first().firstOrNull { it.bookingId == current.bookingId }
                        if (latest != null && latest.status != current.status) resumePayment(latest)
                    }
                }
                .onFailure { throwable ->
                    _historyError.value = throwable.toAppError().userMessage(strings)
                }
            _historyLoading.value = false
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
        if (_bookingState.value is BookingUiState.Loading) return
        val currentRoom = _selectedRoom.value ?: return
        if (checkInDate > 0L && checkOutDate > 0L && checkOutDate <= checkInDate) {
            _bookingState.value = BookingUiState.Error(strings.get(R.string.ngay_tra_phong_phai_sau_ngay_nhan_phong))
            return
        }
        val normalizedCheckIn = checkInDate.takeIf { it > 0L } ?: startOfToday()
        val normalizedCheckOut = checkOutDate.takeIf { it > normalizedCheckIn }
            ?: normalizedCheckIn + DAY_MS

        when {
            quantity <= 0 || quantity > currentRoom.availableRooms -> {
                _bookingState.value = BookingUiState.Error(strings.get(R.string.so_luong_phong_khong_hop_le))
                return
            }
            guests <= 0 -> {
                _bookingState.value = BookingUiState.Error(strings.get(R.string.so_khach_toi_thieu_la_1))
                return
            }
            normalizedCheckOut <= normalizedCheckIn -> {
                _bookingState.value = BookingUiState.Error(strings.get(R.string.ngay_tra_phong_phai_sau_ngay_nhan_phong))
                return
            }
        }

        _bookingState.value = BookingUiState.Loading
        viewModelScope.launch {

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
        if (_paymentState.value is PaymentUiState.Loading || checkingPayment) return
        val booking = _lastBooking.value
        if (booking == null) {
            _paymentState.value = PaymentUiState.Error(strings.get(R.string.khong_tim_thay_booking_e_thanh_toan))
            return
        }

        _paymentState.value = PaymentUiState.Loading
        viewModelScope.launch {

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

    fun startVnPayPayment() {
        if (_paymentState.value is PaymentUiState.Loading || checkingPayment) return
        val booking = _lastBooking.value
        if (booking == null) {
            _paymentState.value = PaymentUiState.Error(strings.get(R.string.khong_tim_thay_booking_e_thanh_toan))
            return
        }

        _paymentState.value = PaymentUiState.Loading
        viewModelScope.launch {
            roomRepository.createVnPayPayment(
                booking = booking,
                idempotencyKey = paymentIdempotencyKey
            )
                .onSuccess { session ->
                    _lastBooking.value = booking.copy(
                        status = "PROCESSING",
                        paymentMethod = PaymentMethod.VNPAY.name,
                        transactionId = null,
                        paidAt = null
                    )
                    _paymentState.value = PaymentUiState.VnPayReady(session)
                }
                .onFailure { throwable ->
                    // A new key is safe here: the backend locks the booking and reuses any
                    // still-active VNPAY transaction, while an expired attempt can be replaced.
                    paymentIdempotencyKey = UUID.randomUUID().toString()
                    _paymentState.value = PaymentUiState.Error(throwable.toPaymentUserMessage())
                }
        }
    }

    fun checkVnPayPaymentStatus() {
        if (checkingPayment || _paymentState.value is PaymentUiState.Loading) return
        val booking = _lastBooking.value ?: return
        val currentSession = when (val currentState = _paymentState.value) {
            is PaymentUiState.VnPayReady -> currentState.session
            is PaymentUiState.VnPayPending -> currentState.session
            else -> null
        }

        checkingPayment = true
        viewModelScope.launch {
            _paymentState.value = PaymentUiState.VnPayPending(
                session = currentSession,
                message = strings.get(R.string.ang_kiem_tra_trang_thai_giao_dich_voi_backend)
            )
            roomRepository.getVnPayPaymentStatus(booking)
                .onSuccess { status ->
                    when (status.status) {
                        "SUCCESS" -> {
                            val result = PaymentResult(
                                bookingId = status.bookingId,
                                status = "SUCCESS",
                                method = PaymentMethod.VNPAY,
                                transactionId = status.transactionId,
                                message = status.message,
                                paidAt = status.paidAt
                            )
                            _lastBooking.value = booking.copy(
                                status = "SUCCESS",
                                paymentMethod = PaymentMethod.VNPAY.name,
                                transactionId = status.transactionId,
                                paidAt = status.paidAt
                            )
                            _paymentState.value = PaymentUiState.Success(result)
                            refreshBookingHistory()
                        }
                        "FAILED" -> {
                            val result = PaymentResult(
                                bookingId = status.bookingId,
                                status = "FAILED",
                                method = PaymentMethod.VNPAY,
                                transactionId = status.transactionId,
                                message = status.message,
                                paidAt = status.paidAt
                            )
                            _lastBooking.value = booking.copy(
                                status = "FAILED",
                                paymentMethod = PaymentMethod.VNPAY.name,
                                transactionId = status.transactionId,
                                paidAt = status.paidAt
                            )
                            paymentIdempotencyKey = UUID.randomUUID().toString()
                            _paymentState.value = PaymentUiState.Failed(result)
                            refreshBookingHistory()
                        }
                        else -> {
                            _paymentState.value = PaymentUiState.VnPayPending(
                                session = currentSession,
                                message = strings.get(R.string.vnpay_chua_gui_xac_nhan_cuoi_cung_hay_oi_vai_giay_roi_k)
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _paymentState.value = PaymentUiState.VnPayPending(
                        session = currentSession,
                        message = throwable.toPaymentUserMessage()
                    )
                }
            checkingPayment = false
        }
    }

    fun handlePaymentReturn(bookingId: Int, onResolved: (Booking?) -> Unit) {
        viewModelScope.launch {
            val result = roomRepository.refreshBookingHistory()
            if (result.isFailure) {
                _historyError.value = result.exceptionOrNull()?.toUserMessage()
                onResolved(null)
                return@launch
            }
            // stateIn may not have collected Room's latest emission yet.
            val bookings = roomRepository.bookingHistory.first()
            val booking = bookings.firstOrNull { it.bookingId == bookingId }
            if (booking == null) _historyError.value = strings.get(R.string.khong_tim_thay_booking_trong_tai_khoan_hien_tai)
            onResolved(booking)
        }
    }

    /** Restores a booking selected from History/deep link into the payment flow. */
    fun resumePayment(booking: Booking) {
        if (_paymentState.value is PaymentUiState.Loading || checkingPayment) return
        _selectedRoom.value = roomRepository.getRoomById(booking.roomId) ?: _selectedRoom.value
        _lastBooking.value = booking
        paymentIdempotencyKey = UUID.randomUUID().toString()
        _paymentState.value = if (booking.status == "SUCCESS") {
            PaymentUiState.Success(PaymentResult(booking.bookingId, "SUCCESS",
                runCatching { PaymentMethod.valueOf(booking.paymentMethod ?: "VNPAY") }.getOrDefault(PaymentMethod.VNPAY),
                booking.transactionId, strings.get(R.string.booking_a_uoc_thanh_toan), booking.paidAt))
        } else if (booking.status == "FAILED") {
            PaymentUiState.Failed(PaymentResult(booking.bookingId, "FAILED",
                runCatching { PaymentMethod.valueOf(booking.paymentMethod ?: "VNPAY") }.getOrDefault(PaymentMethod.VNPAY),
                booking.transactionId, strings.get(R.string.that_bai), booking.paidAt))
        } else if (booking.status == "PROCESSING") {
            PaymentUiState.VnPayPending(
                session = null,
                message = strings.get(R.string.giao_dich_ang_uoc_xu_ly_kiem_tra_trang_thai_e_lay_ket_q)
            )
        } else {
            PaymentUiState.Idle
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
        pricePerNight: Long,
        quantity: Int,
        checkInDate: Long,
        checkOutDate: Long
    ): Long {
        if (quantity <= 0 || checkOutDate <= checkInDate) return 0L
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

    private fun Throwable.toUserMessage(): String = toAppError().userMessage(strings)

    private fun Throwable.toPaymentUserMessage(): String = toAppError().userMessage(strings)

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
