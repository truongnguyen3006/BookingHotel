package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.example.bookinghotel.ui.bookingStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.R
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.toBookingStatusLabel
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toDateLabel
import com.example.bookinghotel.ui.toDateTimeLabel
import com.example.bookinghotel.ui.toRoomLabel

private enum class HistoryFilter {
    ALL,
    PENDING,
    SUCCESS,
    FAILED
}

@Composable
fun BookingHistoryScreen(
    viewModel: BookingViewModel,
    navController: NavController
) {
    val bookings by viewModel.bookingHistory.collectAsState()
    val loading by viewModel.historyLoading.collectAsState()
    val error by viewModel.historyError.collectAsState()
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    LaunchedEffect(Unit) {
        viewModel.refreshBookingHistory()
    }

    val visibleBookings = bookings.filter { booking ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.PENDING -> booking.status == "PENDING_PAYMENT" || booking.status == "PROCESSING"
            HistoryFilter.SUCCESS -> booking.status == "SUCCESS"
            HistoryFilter.FAILED -> booking.status == "FAILED"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (loading) CircularProgressIndicator(modifier = Modifier.testTag("history_loading"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("history_error")) }
        OutlinedButton(onClick = viewModel::refreshBookingHistory, enabled = !loading,
            modifier = Modifier.testTag("history_refresh_button")) { Text(stringResource(R.string.tai_lai)) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryFilter.entries.forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = {
                        Text(
                            when (item) {
                                HistoryFilter.ALL -> stringResource(R.string.tat_ca)
                                HistoryFilter.PENDING -> stringResource(R.string.cho_thanh_toan)
                                HistoryFilter.SUCCESS -> stringResource(R.string.a_thanh_toan)
                                HistoryFilter.FAILED -> stringResource(R.string.that_bai)
                            }
                        )
                    }
                )
            }
        }

        if (visibleBookings.isEmpty() && !loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (bookings.isEmpty()) {
                        stringResource(R.string.ban_chua_co_lich_su_at_phong)
                    } else {
                        stringResource(R.string.khong_co_booking_phu_hop_voi_bo_loc)
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("booking_history_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleBookings, key = { it.bookingId }) { booking ->
                    BookingHistoryItem(
                        booking = booking,
                        onResumePayment = {
                            viewModel.resumePayment(booking)
                            navController.navigate(Screen.Payment.route)
                            if (booking.status == "PROCESSING") {
                                viewModel.checkVnPayPaymentStatus()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingHistoryItem(
    booking: Booking,
    onResumePayment: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("booking_history_item_${booking.bookingId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.booking, booking.bookingId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(booking.status.toBookingStatusLabel()) },
                    modifier = Modifier.testTag("booking_status_${booking.bookingId}").semantics { bookingStatus = booking.status }
                )
            }

            Text(stringResource(R.string.phong_khach, booking.roomTypeKey.toRoomLabel(), booking.quantity, booking.guests))
            Text(stringResource(R.string.em_117af8, booking.checkInDate.toDateLabel(), booking.checkOutDate.toDateLabel(), booking.nights))
            Text(
                text = booking.totalPrice.toCurrencyLabel(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.tao_luc, booking.createdAt.toDateTimeLabel()))

            booking.paymentMethod?.let { Text(stringResource(R.string.phuong_thuc_thanh_toan, it)) }
            booking.transactionId?.let { Text(stringResource(R.string.ma_giao_dich, it)) }
            booking.paidAt?.let { Text(stringResource(R.string.a_thanh_toan_28dff9, it.toDateTimeLabel())) }

            when (booking.status) {
                "PENDING_PAYMENT" -> Button(
                    onClick = onResumePayment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_pay_${booking.bookingId}")
                ) {
                    Text(stringResource(R.string.pay_now))
                }
                "PROCESSING" -> OutlinedButton(
                    onClick = onResumePayment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_check_payment_${booking.bookingId}")
                ) {
                    Text(stringResource(R.string.check_payment))
                }
                "FAILED" -> Button(
                    onClick = onResumePayment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_retry_payment_${booking.bookingId}")
                ) {
                    Text(stringResource(R.string.retry_payment))
                }
            }
        }
    }
}
