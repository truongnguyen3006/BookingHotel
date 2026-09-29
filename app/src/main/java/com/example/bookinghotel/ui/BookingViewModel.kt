package com.example.bookinghotel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface BookingUiState {
    data object Idle : BookingUiState
    data object Loading : BookingUiState
    data object Success : BookingUiState
    data class Error(val message: String) : BookingUiState
}

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val roomRepository: RoomRepository
) : ViewModel() {

    val rooms: StateFlow<List<Room>> = roomRepository.rooms

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

    fun selectRoom(room: Room) {
        _selectedRoom.value = roomRepository.getRoomById(room.id) ?: room
        _bookingState.value = BookingUiState.Idle
    }

    fun bookRoom(quantity: Int) {
        val currentRoom = _selectedRoom.value ?: return

        if (quantity <= 0 || quantity > currentRoom.availableRooms) {
            _bookingState.value = BookingUiState.Error("Số lượng phòng không hợp lệ.")
            return
        }

        viewModelScope.launch {
            _bookingState.value = BookingUiState.Loading

            roomRepository.bookRoom(currentRoom.id, quantity)
                .onSuccess { booking ->
                    _selectedRoom.value = roomRepository.getRoomById(booking.roomId)
                    _quantity.value = booking.quantity
                    _lastBooking.value = booking
                    _bookingState.value = BookingUiState.Success
                }
                .onFailure { throwable ->
                    _bookingState.value = BookingUiState.Error(throwable.toUserMessage())
                }
        }
    }

    fun consumeBookingSuccess() {
        if (_bookingState.value is BookingUiState.Success) {
            _bookingState.value = BookingUiState.Idle
        }
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is IOException -> "Không thể kết nối tới máy chủ. Hãy kiểm tra mock server và thử lại."
            is HttpException -> when (code()) {
                409 -> "Số phòng trên máy chủ vừa thay đổi. Vui lòng thử lại."
                404 -> "Không tìm thấy dữ liệu yêu cầu trên máy chủ."
                else -> "Máy chủ trả về lỗi HTTP ${code()}."
            }
            else -> message ?: "Đã xảy ra lỗi. Vui lòng thử lại."
        }
    }
}
