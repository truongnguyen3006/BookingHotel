package com.example.bookinghotel.ui

import com.example.bookinghotel.ui.AppStrings
import com.example.bookinghotel.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookinghotel.data.AdminBooking
import com.example.bookinghotel.data.AdminDashboard
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.remote.toAppError
import com.example.bookinghotel.data.remote.userMessage
import com.example.bookinghotel.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val dashboard: AdminDashboard? = null,
    val rooms: List<Room> = emptyList(),
    val bookings: List<AdminBooking> = emptyList(),
    val loadingDashboard: Boolean = false,
    val loadingRooms: Boolean = false,
    val loadingBookings: Boolean = false,
    val savingRoomId: Int? = null,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: AdminRepository,
    private val strings: AppStrings
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    fun refreshDashboard() {
        viewModelScope.launch {
            _state.update { it.copy(loadingDashboard = true, message = null, isError = false) }
            repository.getDashboard()
                .onSuccess { dashboard ->
                    _state.update { it.copy(dashboard = dashboard, loadingDashboard = false) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            loadingDashboard = false,
                            message = throwable.toAppError().userMessage(strings),
                            isError = true
                        )
                    }
                }
        }
    }

    fun refreshRooms() {
        viewModelScope.launch {
            _state.update { it.copy(loadingRooms = true, message = null, isError = false) }
            repository.getRooms()
                .onSuccess { rooms ->
                    _state.update { it.copy(rooms = rooms, loadingRooms = false) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            loadingRooms = false,
                            message = throwable.toAppError().userMessage(strings),
                            isError = true
                        )
                    }
                }
        }
    }

    fun refreshBookings() {
        viewModelScope.launch {
            _state.update { it.copy(loadingBookings = true, message = null, isError = false) }
            repository.getBookings()
                .onSuccess { bookings ->
                    _state.update { it.copy(bookings = bookings, loadingBookings = false) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            loadingBookings = false,
                            message = throwable.toAppError().userMessage(strings),
                            isError = true
                        )
                    }
                }
        }
    }

    fun updateRoom(roomId: Int, pricePerNight: Long, availableRooms: Int) {
        if (pricePerNight <= 0L) {
            _state.update { it.copy(message = strings.get(R.string.gia_phong_phai_lon_hon_0), isError = true) }
            return
        }
        if (availableRooms < 0) {
            _state.update { it.copy(message = strings.get(R.string.so_phong_con_lai_khong_the_am), isError = true) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(savingRoomId = roomId, message = null, isError = false) }
            repository.updateRoom(roomId, pricePerNight, availableRooms)
                .onSuccess { updated ->
                    _state.update { current ->
                        current.copy(
                            rooms = current.rooms.map { if (it.id == updated.id) updated else it },
                            savingRoomId = null,
                            message = strings.get(R.string.a_cap_nhat_phong, updated.typeKey),
                            isError = false
                        )
                    }
                    refreshDashboard()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            savingRoomId = null,
                            message = throwable.toAppError().userMessage(strings),
                            isError = true
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, isError = false) }
    }
}
