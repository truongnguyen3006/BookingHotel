package com.example.bookinghotel

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.repository.InMemoryRoomRepository
import com.example.bookinghotel.data.repository.RoomDataSource
import com.example.bookinghotel.data.repository.RoomRepository
import com.example.bookinghotel.ui.BookingUiState
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.PaymentUiState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun bookRoom_validQuantity_updatesViewModelState() {
        val repository = InMemoryRoomRepository()
        val viewModel = BookingViewModel(repository)
        viewModel.selectRoom(repository.getRoomById(1)!!)

        viewModel.bookRoom(quantity = 2)

        assertTrue(viewModel.bookingState.value is BookingUiState.Success)
        assertEquals(2, viewModel.quantity.value)
        assertEquals(8, viewModel.selectedRoom.value?.availableRooms)
        assertEquals("PENDING_PAYMENT", viewModel.lastBooking.value?.status)
    }

    @Test
    fun bookRoom_invalidQuantity_returnsValidationErrorWithoutChangingInventory() {
        val repository = InMemoryRoomRepository()
        val viewModel = BookingViewModel(repository)
        viewModel.selectRoom(repository.getRoomById(1)!!)

        viewModel.bookRoom(quantity = -1)

        val state = viewModel.bookingState.value
        assertTrue(state is BookingUiState.Error)
        assertEquals("Số lượng phòng không hợp lệ.", (state as BookingUiState.Error).message)
        assertEquals(10, repository.getRoomById(1)?.availableRooms)
    }

    @Test
    fun payment_failedThenRetried_movesFromFailedToSuccess() {
        val repository = InMemoryRoomRepository()
        val viewModel = BookingViewModel(repository)
        viewModel.selectRoom(repository.getRoomById(1)!!)
        viewModel.bookRoom(quantity = 1)

        viewModel.payBooking(
            method = PaymentMethod.CARD,
            simulateFailure = true
        )

        assertTrue(viewModel.paymentState.value is PaymentUiState.Failed)
        assertEquals("FAILED", viewModel.lastBooking.value?.status)

        viewModel.payBooking(
            method = PaymentMethod.QR,
            simulateFailure = false
        )

        assertTrue(viewModel.paymentState.value is PaymentUiState.Success)
        assertEquals("SUCCESS", viewModel.lastBooking.value?.status)
    }

    @Test
    fun loadRooms_ioFailure_exposesFriendlyErrorMessage() {
        val viewModel = BookingViewModel(
            ConfigurableRepository(
                refreshResult = Result.failure(IOException("offline"))
            )
        )

        assertEquals(
            "Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.",
            viewModel.roomLoadError.value
        )
        assertEquals(false, viewModel.isLoadingRooms.value)
    }

    @Test
    fun payBooking_withoutBooking_returnsErrorImmediately() {
        val viewModel = BookingViewModel(InMemoryRoomRepository())

        viewModel.payBooking(
            method = PaymentMethod.CARD,
            simulateFailure = false
        )

        val state = viewModel.paymentState.value
        assertTrue(state is PaymentUiState.Error)
        assertEquals(
            "Không tìm thấy booking để thanh toán.",
            (state as PaymentUiState.Error).message
        )
    }

    private class ConfigurableRepository(
        private val refreshResult: Result<Unit>
    ) : RoomRepository {
        private val _rooms = MutableStateFlow<List<Room>>(emptyList())
        override val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

        private val _roomDataSource = MutableStateFlow(RoomDataSource.EMPTY)
        override val roomDataSource: StateFlow<RoomDataSource> = _roomDataSource.asStateFlow()

        private val _lastRoomSyncAt = MutableStateFlow<Long?>(null)
        override val lastRoomSyncAt: StateFlow<Long?> = _lastRoomSyncAt.asStateFlow()

        private val _history = MutableStateFlow<List<Booking>>(emptyList())
        override val bookingHistory: Flow<List<Booking>> = _history

        override suspend fun refreshRooms(): Result<Unit> = refreshResult
        override suspend fun syncRoomsFromNetwork(): Result<Unit> = refreshResult
        override suspend fun refreshBookingHistory(): Result<Unit> = Result.success(Unit)

        override fun getRoomById(roomId: Int): Room? = null

        override suspend fun bookRoom(
            roomId: Int,
            quantity: Int,
            checkInDate: Long,
            checkOutDate: Long,
            guests: Int
        ): Result<Booking> = Result.failure(UnsupportedOperationException())

        override suspend fun payBooking(
            booking: Booking,
            method: PaymentMethod,
            simulateFailure: Boolean,
            idempotencyKey: String
        ): Result<PaymentResult> = Result.failure(UnsupportedOperationException())
    }
}
