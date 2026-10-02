package com.example.bookinghotel

import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.PaymentResult
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.data.VnPayPaymentSession
import com.example.bookinghotel.data.VnPayPaymentStatus
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
        val viewModel = BookingViewModel(repository, TestAppStrings)
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
        val viewModel = BookingViewModel(repository, TestAppStrings)
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
        val viewModel = BookingViewModel(repository, TestAppStrings)
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
            ), TestAppStrings
        )

        assertEquals(
            "Không thể kết nối tới máy chủ. Vui lòng kiểm tra kết nối và thử lại.",
            viewModel.roomLoadError.value
        )
        assertEquals(false, viewModel.isLoadingRooms.value)
    }

    @Test
    fun payBooking_withoutBooking_returnsErrorImmediately() {
        val viewModel = BookingViewModel(InMemoryRoomRepository(), TestAppStrings)

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

    @Test
    fun rapidDoubleSubmit_createsOnlyOneBookingWhileRequestIsPending() = kotlinx.coroutines.test.runTest {
        val delegate = InMemoryRoomRepository()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : RoomRepository by delegate {
            override suspend fun bookRoom(roomId: Int, quantity: Int, checkInDate: Long, checkOutDate: Long, guests: Int): Result<Booking> {
                calls++
                release.await()
                return delegate.bookRoom(roomId, quantity, checkInDate, checkOutDate, guests)
            }
        }
        val vm = BookingViewModel(repository, TestAppStrings)
        vm.selectRoom(delegate.getRoomById(1)!!)
        vm.bookRoom(1); vm.bookRoom(1); vm.bookRoom(1)
        assertEquals(1, calls)
        assertTrue(vm.bookingState.value is BookingUiState.Loading)
        release.complete(Unit)
        assertTrue(vm.bookingState.value is BookingUiState.Success)
        assertEquals(9, delegate.getRoomById(1)?.availableRooms)
    }

    @Test
    fun paymentReturn_fetchesOwnedHistoryBeforeResolvingForeignId() {
        val delegate = InMemoryRoomRepository()
        var refreshes = 0
        val repository = object : RoomRepository by delegate {
            override suspend fun refreshBookingHistory(): Result<Unit> {
                refreshes++
                return Result.success(Unit)
            }
        }
        val vm = BookingViewModel(repository, TestAppStrings)
        var resolved: Booking? = null
        vm.handlePaymentReturn(999) { resolved = it }
        assertEquals(1, refreshes)
        assertEquals(null, resolved)
        assertTrue(vm.historyError.value != null)
    }

    @Test
    fun paymentReturn_offlineDoesNotTrustPreviouslyCachedSuccess() {
        val delegate = InMemoryRoomRepository()
        val vmForBooking = BookingViewModel(delegate, TestAppStrings)
        vmForBooking.selectRoom(delegate.getRoomById(1)!!)
        vmForBooking.bookRoom(1)
        val booking = vmForBooking.lastBooking.value!!
        vmForBooking.payBooking(PaymentMethod.CARD, false)
        val repository = object : RoomRepository by delegate {
            override suspend fun refreshBookingHistory(): Result<Unit> = Result.failure(IOException("offline"))
        }
        val vm = BookingViewModel(repository, TestAppStrings)
        var resolved: Booking? = booking
        vm.handlePaymentReturn(booking.bookingId) { resolved = it }
        assertEquals(null, resolved)
        assertTrue(vm.historyError.value != null)
        assertTrue(vm.paymentState.value is PaymentUiState.Idle)
    }

    @Test
    fun paymentReturn_repeatedTerminalResultUsesBackendHistoryWithoutStartingPayment() {
        val delegate = InMemoryRoomRepository()
        val vm = BookingViewModel(delegate, TestAppStrings)
        vm.selectRoom(delegate.getRoomById(1)!!)
        vm.bookRoom(1)
        vm.payBooking(PaymentMethod.CARD, false)
        val id = vm.lastBooking.value!!.bookingId
        repeat(2) {
            vm.handlePaymentReturn(id) { vm.resumePayment(requireNotNull(it)) }
            assertTrue(vm.paymentState.value is PaymentUiState.Success)
        }
        assertEquals(9, delegate.getRoomById(1)?.availableRooms)
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

        override suspend fun createVnPayPayment(
            booking: Booking,
            idempotencyKey: String
        ): Result<VnPayPaymentSession> = Result.failure(UnsupportedOperationException())

        override suspend fun getVnPayPaymentStatus(
            booking: Booking
        ): Result<VnPayPaymentStatus> = Result.failure(UnsupportedOperationException())
    }
}
