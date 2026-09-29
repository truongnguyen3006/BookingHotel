package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.PaymentUiState
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.toBookingStatusLabel
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toDateTimeLabel

@Composable
fun PaymentScreen(
    viewModel: BookingViewModel,
    navController: NavController
) {
    val booking by viewModel.lastBooking.collectAsState()
    val paymentState by viewModel.paymentState.collectAsState()
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CARD) }
    var simulateFailure by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

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

    val currentBooking = booking!!
    val isProcessing = paymentState is PaymentUiState.Loading

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showConfirmDialog = false },
            title = { Text("Xác nhận thanh toán") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Booking #${currentBooking.bookingId}")
                    Text("Phương thức: ${if (selectedMethod == PaymentMethod.CARD) "Thẻ" else "QR"}")
                    Text("Số tiền: ${currentBooking.totalPrice.toCurrencyLabel()}", fontWeight = FontWeight.Bold)
                    Text("Mỗi lần bấm thanh toán được bảo vệ bằng idempotency key để tránh ghi nhận trùng giao dịch.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.payBooking(
                            method = selectedMethod,
                            simulateFailure = simulateFailure
                        )
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("confirm_payment_button")
                ) {
                    Text("Thanh toán")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !isProcessing
                ) {
                    Text("Hủy")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Booking #${currentBooking.bookingId}", fontWeight = FontWeight.Bold)
                Text("Số tiền cần thanh toán")
                Text(
                    currentBooking.totalPrice.toCurrencyLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text("Trạng thái: ${currentBooking.status.toBookingStatusLabel()}")
            }
        }

        Text("Phương thức thanh toán", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = selectedMethod == PaymentMethod.CARD,
                onClick = { if (!isProcessing) selectedMethod = PaymentMethod.CARD },
                label = { Text("Thẻ") },
                enabled = !isProcessing
            )
            FilterChip(
                selected = selectedMethod == PaymentMethod.QR,
                onClick = { if (!isProcessing) selectedMethod = PaymentMethod.QR },
                label = { Text("QR") },
                enabled = !isProcessing
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chế độ demo lỗi thanh toán", fontWeight = FontWeight.Medium)
                    Text(
                        text = "Bật để kiểm thử FAILED → Retry. Không phải cổng thanh toán thật.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = simulateFailure,
                    onCheckedChange = { simulateFailure = it },
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("simulate_failure_switch")
                )
            }
        }

        when (val state = paymentState) {
            PaymentUiState.Idle -> {
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_submit_button")
                ) {
                    Text("Thanh toán ${currentBooking.totalPrice.toCurrencyLabel()}")
                }
            }

            PaymentUiState.Loading -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Column {
                            Text("Đang xử lý giao dịch", fontWeight = FontWeight.Bold)
                            Text("Vui lòng không đóng ứng dụng hoặc bấm thanh toán nhiều lần.")
                        }
                    }
                }
            }

            is PaymentUiState.Success -> {
                PaymentReceipt(
                    method = state.result.method,
                    transactionId = state.result.transactionId,
                    paidAt = state.result.paidAt,
                    message = state.result.message
                )
                Button(
                    onClick = { navController.navigate(Screen.History.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_history_button")
                ) {
                    Text("Xem lịch sử đặt phòng")
                }
                OutlinedButton(
                    onClick = { navController.popBackStack(Screen.List.route, false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Về màn hình chính")
                }
            }

            is PaymentUiState.Failed -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Thanh toán thất bại",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(state.result.message)
                        Text("Bạn có thể thử lại. Lần retry sẽ sử dụng một payment attempt mới.")
                    }
                }
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Thử thanh toán lại")
                }
            }

            is PaymentUiState.Error -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Không thể xác nhận kết quả giao dịch",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(state.message)
                        Text("Nút Thử lại giữ nguyên idempotency key để server không tạo giao dịch trùng nếu request trước đã được xử lý.")
                    }
                }
                Button(
                    onClick = {
                        viewModel.payBooking(
                            method = selectedMethod,
                            simulateFailure = simulateFailure
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Thử lại an toàn")
                }
            }
        }
    }
}

@Composable
private fun PaymentReceipt(
    method: PaymentMethod,
    transactionId: String?,
    paidAt: Long?,
    message: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "Thanh toán thành công",
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text("Phương thức: ${if (method == PaymentMethod.CARD) "Thẻ" else "QR"}")
            Text("Mã giao dịch: ${transactionId ?: "--"}")
            Text("Thời gian: ${paidAt?.toDateTimeLabel() ?: "--"}")
            Text(message)
        }
    }
}
