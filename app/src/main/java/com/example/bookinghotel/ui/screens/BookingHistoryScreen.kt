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
                                HistoryFilter.ALL -> "Tất cả"
                                HistoryFilter.PENDING -> "Chờ thanh toán"
                                HistoryFilter.SUCCESS -> "Đã thanh toán"
                                HistoryFilter.FAILED -> "Thất bại"
                            }
                        )
                    }
                )
            }
        }

        if (visibleBookings.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (bookings.isEmpty()) {
                        "Bạn chưa có lịch sử đặt phòng."
                    } else {
                        "Không có booking phù hợp với bộ lọc."
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
                    text = "Booking #${booking.bookingId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(booking.status.toBookingStatusLabel()) },
                    modifier = Modifier.testTag("booking_status_${booking.bookingId}")
                )
            }

            Text("${booking.roomTypeKey.toRoomLabel()} • ${booking.quantity} phòng • ${booking.guests} khách")
            Text("${booking.checkInDate.toDateLabel()} → ${booking.checkOutDate.toDateLabel()} • ${booking.nights} đêm")
            Text(
                text = booking.totalPrice.toCurrencyLabel(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text("Tạo lúc: ${booking.createdAt.toDateTimeLabel()}")

            booking.paymentMethod?.let { Text("Phương thức thanh toán: $it") }
            booking.transactionId?.let { Text("Mã giao dịch: $it") }
            booking.paidAt?.let { Text("Đã thanh toán: ${it.toDateTimeLabel()}") }

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
