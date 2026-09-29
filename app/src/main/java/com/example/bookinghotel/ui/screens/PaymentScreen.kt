package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.PaymentUiState
import com.example.bookinghotel.ui.Screen

@Composable
fun PaymentScreen(
    viewModel: BookingViewModel,
    navController: NavController
) {
    val booking = viewModel.lastBooking.collectAsState().value
    val paymentState = viewModel.paymentState.collectAsState().value
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CARD) }
    var simulateFailure by remember { mutableStateOf(false) }

    if (booking == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Không tìm thấy booking để thanh toán.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack(Screen.List.route, false) }) {
                Text("Về màn hình chính")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Thanh toán booking #${booking.bookingId}",
            fontWeight = FontWeight.Bold
        )
        Text("Tổng tiền: \$${booking.totalPrice}")
        Text("Trạng thái hiện tại: ${booking.status}")

        Text("Phương thức thanh toán", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = selectedMethod == PaymentMethod.CARD,
                onClick = { selectedMethod = PaymentMethod.CARD },
                label = { Text("Thẻ") }
            )
            FilterChip(
                selected = selectedMethod == PaymentMethod.QR,
                onClick = { selectedMethod = PaymentMethod.QR },
                label = { Text("QR") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mô phỏng thanh toán thất bại")
                Text(
                    text = "Chỉ dùng để test luồng FAILED → Retry",
                    color = Color.Gray
                )
            }
            Switch(
                checked = simulateFailure,
                onCheckedChange = { simulateFailure = it },
                modifier = Modifier.testTag("simulate_failure_switch")
            )
        }

        when (val state = paymentState) {
            PaymentUiState.Idle -> {
                Button(
                    onClick = {
                        viewModel.payBooking(
                            method = selectedMethod,
                            simulateFailure = simulateFailure
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_submit_button")
                ) {
                    Text("Thanh toán ngay")
                }
            }

            PaymentUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                Text("Đang xử lý thanh toán...")
            }

            is PaymentUiState.Success -> {
                Text(
                    text = "Thanh toán thành công",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
                state.result.transactionId?.let {
                    Text("Mã giao dịch: $it")
                }
                Text(state.result.message)
                Button(
                    onClick = { navController.navigate(Screen.History.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_history_button")
                ) {
                    Text("Xem lịch sử đặt phòng")
                }
                Button(
                    onClick = { navController.popBackStack(Screen.List.route, false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Về màn hình chính")
                }
            }

            is PaymentUiState.Failed -> {
                Text(
                    text = "Thanh toán thất bại",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Text(state.result.message)
                Button(
                    onClick = {
                        viewModel.payBooking(
                            method = selectedMethod,
                            simulateFailure = simulateFailure
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Thử thanh toán lại")
                }
            }

            is PaymentUiState.Error -> {
                Text(text = state.message, color = Color.Red)
                Button(
                    onClick = {
                        viewModel.payBooking(
                            method = selectedMethod,
                            simulateFailure = simulateFailure
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Thử lại")
                }
            }
        }
    }
}
