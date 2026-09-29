package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.toBookingStatusLabel
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toDateLabel
import com.example.bookinghotel.ui.toDateTimeLabel

@Composable
fun BookingSummaryScreen(viewModel: BookingViewModel, navController: NavController) {
    val room by viewModel.selectedRoom.collectAsState()
    val booking by viewModel.lastBooking.collectAsState()

    if (room == null || booking == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Không tìm thấy thông tin booking.")
            OutlinedButton(
                onClick = { navController.popBackStack(Screen.List.route, inclusive = false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Về danh sách phòng")
            }
        }
        return
    }

    val currentRoom = room!!
    val currentBooking = booking!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Đặt phòng thành công",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text("Mã booking #${currentBooking.bookingId}")

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(currentRoom.type),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                SummaryLine("Thời gian", "${currentBooking.checkInDate.toDateLabel()} → ${currentBooking.checkOutDate.toDateLabel()}")
                SummaryLine("Số đêm", "${currentBooking.nights}")
                SummaryLine("Số phòng", "${currentBooking.quantity}")
                SummaryLine("Số khách", "${currentBooking.guests}")
                SummaryLine("Giá/đêm", currentBooking.pricePerNight.toCurrencyLabel())
                SummaryLine("Tổng tiền", currentBooking.totalPrice.toCurrencyLabel(), emphasized = true)
                SummaryLine("Trạng thái", currentBooking.status.toBookingStatusLabel())
            }
        }

        if (currentBooking.status == "SUCCESS") {
            PaymentReceiptCard(
                paymentMethod = currentBooking.paymentMethod,
                transactionId = currentBooking.transactionId,
                paidAt = currentBooking.paidAt
            )
        } else {
            Text(
                text = "Booking đang giữ chỗ. Hãy hoàn tất thanh toán để xác nhận giao dịch.",
                color = MaterialTheme.colorScheme.secondary
            )
            Button(
                onClick = {
                    viewModel.preparePayment()
                    navController.navigate(Screen.Payment.route)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summary_pay_button")
            ) {
                Text("Thanh toán ${currentBooking.totalPrice.toCurrencyLabel()}")
            }
        }

        OutlinedButton(
            onClick = { navController.navigate(Screen.History.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Xem lịch sử đặt phòng")
        }

        OutlinedButton(
            onClick = { navController.popBackStack(Screen.List.route, inclusive = false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Quay về màn hình chính")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryLine(label: String, value: String, emphasized: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PaymentReceiptCard(
    paymentMethod: String?,
    transactionId: String?,
    paidAt: Long?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Biên nhận thanh toán", fontWeight = FontWeight.Bold)
            Text("Phương thức: ${paymentMethod ?: "--"}")
            Text("Mã giao dịch: ${transactionId ?: "--"}")
            Text("Thời gian: ${paidAt?.toDateTimeLabel() ?: "--"}")
        }
    }
}
