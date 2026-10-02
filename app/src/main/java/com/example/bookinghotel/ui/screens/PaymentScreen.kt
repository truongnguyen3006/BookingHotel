package com.example.bookinghotel.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.R
import com.example.bookinghotel.BuildConfig
import com.example.bookinghotel.data.PaymentMethod
import com.example.bookinghotel.data.VnPayPaymentSession
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.PaymentUiState
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.toBookingStatusLabel
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toDateTimeLabel
import com.example.bookinghotel.ui.toVndLabel

@Composable
fun PaymentScreen(
    viewModel: BookingViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val booking by viewModel.lastBooking.collectAsState()
    val paymentState by viewModel.paymentState.collectAsState()
    var selectedMethod by remember { mutableStateOf(if (BuildConfig.DEMO_PAYMENTS_ENABLED) PaymentMethod.CARD else PaymentMethod.VNPAY) }
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
            Text(stringResource(R.string.khong_tim_thay_booking_e_thanh_toan))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack(Screen.List.route, false) }) {
                Text(stringResource(R.string.ve_man_hinh_chinh))
            }
        }
        return
    }

    val currentBooking = booking!!
    LaunchedEffect(currentBooking.bookingId, currentBooking.status, currentBooking.paymentMethod) {
        if (currentBooking.status == "PROCESSING" || currentBooking.paymentMethod == PaymentMethod.VNPAY.name) {
            selectedMethod = PaymentMethod.VNPAY
            simulateFailure = false
        }
    }
    val isProcessing = paymentState is PaymentUiState.Loading ||
        paymentState is PaymentUiState.VnPayReady ||
        paymentState is PaymentUiState.VnPayPending

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showConfirmDialog = false },
            title = { Text(stringResource(R.string.xac_nhan_thanh_toan)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.booking, currentBooking.bookingId))
                    Text(stringResource(R.string.phuong_thuc, paymentMethodLabel(selectedMethod)))
                    Text(stringResource(R.string.gia_tri_booking, currentBooking.totalPrice.toCurrencyLabel()), fontWeight = FontWeight.Bold)
                    if (selectedMethod == PaymentMethod.VNPAY) {
                        Text(stringResource(R.string.vnpay_se_mo_tren_trinh_duyet_quay_lai_ung_dung_e_kiem_t))
                    } else {
                        Text(stringResource(R.string.kiem_tra_thong_tin_thanh_toan_truoc_khi_xac_nhan))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        if (selectedMethod == PaymentMethod.VNPAY) {
                            viewModel.startVnPayPayment()
                        } else {
                            viewModel.payBooking(
                                method = selectedMethod,
                                simulateFailure = simulateFailure
                            )
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("confirm_payment_button")
                ) {
                    Text(if (selectedMethod == PaymentMethod.VNPAY) stringResource(R.string.mo_vnpay) else stringResource(R.string.pay_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !isProcessing
                ) {
                    Text(stringResource(R.string.huy))
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
                Text(stringResource(R.string.booking, currentBooking.bookingId), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.so_tien_booking))
                Text(
                    currentBooking.totalPrice.toCurrencyLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.trang_thai_a9ff17, currentBooking.status.toBookingStatusLabel()))
            }
        }

        Text(stringResource(R.string.phuong_thuc_thanh_toan_c216d3), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (BuildConfig.DEMO_PAYMENTS_ENABLED) {
                FilterChip(
                    selected = selectedMethod == PaymentMethod.CARD,
                    onClick = { if (!isProcessing) selectedMethod = PaymentMethod.CARD },
                    label = { Text(stringResource(R.string.the_demo)) },
                    enabled = !isProcessing
                )
                FilterChip(
                    selected = selectedMethod == PaymentMethod.QR,
                    onClick = { if (!isProcessing) selectedMethod = PaymentMethod.QR },
                    label = { Text(stringResource(R.string.qr_demo)) },
                    enabled = !isProcessing
                )
            }
            FilterChip(
                selected = selectedMethod == PaymentMethod.VNPAY,
                onClick = {
                    if (!isProcessing) {
                        selectedMethod = PaymentMethod.VNPAY
                        simulateFailure = false
                    }
                },
                label = { Text(stringResource(R.string.vnpay)) },
                enabled = !isProcessing
            )
        }

        if (selectedMethod != PaymentMethod.VNPAY) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.che_o_demo_loi_thanh_toan), fontWeight = FontWeight.Medium)
                        Text(
                            text = stringResource(R.string.bat_e_kiem_thu_failed_retry_cho_payment_mo_phong),
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
        } else {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.vnpay), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.so_tien_thanh_toan_uoc_hien_thi_bang_vnd_kiem_tra_thong))
                    Text(stringResource(R.string.ket_qua_success_chi_uoc_ghi_nhan_sau_khi_backend_nhan_i))
                }
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
                    Text(
                        if (selectedMethod == PaymentMethod.VNPAY) {
                            stringResource(R.string.thanh_toan_bang_vnpay)
                        } else {
                            stringResource(R.string.thanh_toan, currentBooking.totalPrice.toCurrencyLabel())
                        }
                    )
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
                            Text(stringResource(R.string.ang_xu_ly_giao_dich), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.vui_long_khong_bam_thanh_toan_nhieu_lan))
                        }
                    }
                }
            }

            is PaymentUiState.VnPayReady -> {
                LaunchedEffect(state.session.paymentUrl) {
                    openExternalPayment(context, state.session.paymentUrl)
                }
                VnPayWaitingCard(
                    session = state.session,
                    message = stringResource(R.string.trang_vnpay_sandbox_a_uoc_mo_sau_khi_hoan_tat_quay_lai),
                    onCheck = viewModel::checkVnPayPaymentStatus,
                    onReopen = { openExternalPayment(context, state.session.paymentUrl) }
                )
            }

            is PaymentUiState.VnPayPending -> {
                VnPayWaitingCard(
                    session = state.session,
                    message = state.message,
                    onCheck = viewModel::checkVnPayPaymentStatus,
                    onReopen = state.session?.let { session ->
                        { openExternalPayment(context, session.paymentUrl) }
                    }
                )
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
                    Text(stringResource(R.string.xem_lich_su_at_phong))
                }
                OutlinedButton(
                    onClick = { navController.popBackStack(Screen.List.route, false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ve_man_hinh_chinh))
                }
            }

            is PaymentUiState.Failed -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.thanh_toan_that_bai),
                            modifier = Modifier.testTag("payment_failed_message"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(state.result.message)
                        Text(stringResource(R.string.ban_co_the_tao_mot_payment_attempt_moi_va_thu_lai))
                    }
                }
                Button(
                    onClick = {
                        if (state.result.method == PaymentMethod.VNPAY) {
                            selectedMethod = PaymentMethod.VNPAY
                            viewModel.startVnPayPayment()
                        } else {
                            showConfirmDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("payment_retry_button")
                ) {
                    Text(stringResource(R.string.retry_payment))
                }
            }

            is PaymentUiState.Error -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.khong_the_xac_nhan_ket_qua_giao_dich),
                            modifier = Modifier.testTag("payment_error_message"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(state.message)
                        Text(
                            if (selectedMethod == PaymentMethod.VNPAY) {
                                stringResource(R.string.backend_se_tai_su_dung_giao_dich_vnpay_con_hieu_luc_att)
                            } else {
                                stringResource(R.string.retry_giu_nguyen_idempotency_key_e_backend_khong_tao_gi)
                            }
                        )
                    }
                }
                Button(
                    onClick = {
                        if (selectedMethod == PaymentMethod.VNPAY) {
                            viewModel.startVnPayPayment()
                        } else {
                            viewModel.payBooking(
                                method = selectedMethod,
                                simulateFailure = simulateFailure
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.thu_lai_an_toan))
                }
            }
        }
    }
}

@Composable
private fun VnPayWaitingCard(
    session: VnPayPaymentSession?,
    message: String,
    onCheck: () -> Unit,
    onReopen: (() -> Unit)?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(stringResource(R.string.ang_cho_vnpay_xac_nhan), fontWeight = FontWeight.Bold)
            session?.let {
                Text(stringResource(R.string.ma_tham_chieu, it.txnRef))
                Text(stringResource(R.string.so_tien, it.amountVnd.toVndLabel()))
                Text(stringResource(R.string.het_han, it.expiresAt.toDateTimeLabel()))
            }
            Text(message)
        }
    }
    Button(
        onClick = onCheck,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vnpay_check_status_button")
    ) {
        Text(stringResource(R.string.check_vnpay_result))
    }
    if (onReopen != null) {
        OutlinedButton(
            onClick = onReopen,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vnpay_reopen_button")
        ) {
            Text(stringResource(R.string.reopen_vnpay))
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
                text = stringResource(R.string.payment_success),
                modifier = Modifier.testTag("payment_success_message"),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.phuong_thuc, paymentMethodLabel(method)))
            Text(stringResource(R.string.ma_giao_dich, transactionId ?: "--"))
            Text(stringResource(R.string.thoi_gian_577a83, paidAt?.toDateTimeLabel() ?: "--"))
            Text(message)
        }
    }
}

@Composable
private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CARD -> stringResource(R.string.the_demo)
    PaymentMethod.QR -> stringResource(R.string.qr_demo)
    PaymentMethod.VNPAY -> stringResource(R.string.vnpay)
}

private fun openExternalPayment(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(context, context.getString(R.string.browser_unavailable), android.widget.Toast.LENGTH_LONG).show()
    }
}
