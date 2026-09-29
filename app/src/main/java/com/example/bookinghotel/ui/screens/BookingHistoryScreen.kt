package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookinghotel.data.Booking
import com.example.bookinghotel.ui.BookingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookingHistoryScreen(viewModel: BookingViewModel) {
    val bookings = viewModel.bookingHistory.collectAsState().value

    if (bookings.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Bạn chưa có lịch sử đặt phòng.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bookings, key = { it.localId }) { booking ->
            BookingHistoryItem(booking)
        }
    }
}

@Composable
private fun BookingHistoryItem(booking: Booking) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Booking #${booking.bookingId}",
                fontWeight = FontWeight.Bold
            )
            Text("Loại phòng: ${booking.roomTypeKey.toRoomLabel()}")
            Text("Số lượng: ${booking.quantity}")
            Text("Giá mỗi đêm: \$${booking.pricePerNight}")
            Text("Tổng tiền: \$${booking.totalPrice}")
            Text("Trạng thái: ${booking.status.toStatusLabel()}")
            Text("Thời gian: ${booking.createdAt.toDateTimeLabel()}")
        }
    }
}

private fun String.toRoomLabel(): String {
    return when (this) {
        "standard" -> "Standard Room"
        "deluxe" -> "Deluxe Room"
        "suite" -> "Suite Room"
        "executive" -> "Executive Room"
        "family" -> "Family Room"
        else -> this
    }
}

private fun String.toStatusLabel(): String {
    return when (this) {
        "PENDING_PAYMENT" -> "Chờ thanh toán"
        "SUCCESS" -> "Thành công"
        "FAILED" -> "Thất bại"
        else -> this
    }
}

private fun Long.toDateTimeLabel(): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(this))
}
