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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.ui.BookingUiState
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toDateLabel
import java.util.Calendar
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailsScreen(viewModel: BookingViewModel, navController: NavController) {
    val room by viewModel.selectedRoom.collectAsState()
    val bookingState by viewModel.bookingState.collectAsState()

    val today = remember { startOfToday() }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var guests by rememberSaveable { mutableStateOf("2") }
    var checkInDate by rememberSaveable { mutableStateOf(today) }
    var checkOutDate by rememberSaveable { mutableStateOf(today + DAY_MS) }
    var showCheckInPicker by rememberSaveable { mutableStateOf(false) }
    var showCheckOutPicker by rememberSaveable { mutableStateOf(false) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(bookingState) {
        if (bookingState is BookingUiState.Success) {
            navController.navigate(Screen.Summary.route)
            viewModel.consumeBookingSuccess()
        }
    }

    if (showCheckInPicker) {
        BookingDatePickerDialog(
            initialDate = checkInDate,
            onDismiss = { showCheckInPicker = false },
            onDateSelected = { selected ->
                checkInDate = selected
                if (checkOutDate <= selected) {
                    checkOutDate = selected + DAY_MS
                }
                showCheckInPicker = false
            }
        )
    }

    if (showCheckOutPicker) {
        BookingDatePickerDialog(
            initialDate = checkOutDate,
            onDismiss = { showCheckOutPicker = false },
            onDateSelected = { selected ->
                checkOutDate = selected
                showCheckOutPicker = false
            }
        )
    }

    room?.let { currentRoom ->
        val quantityValue = quantity.toIntOrNull() ?: 0
        val guestValue = guests.toIntOrNull() ?: 0
        val nights = calculateNights(checkInDate, checkOutDate)
        val estimatedTotal = viewModel.estimatedTotal(
            pricePerNight = currentRoom.pricePerNight,
            quantity = quantityValue,
            checkInDate = checkInDate,
            checkOutDate = checkOutDate
        )

        val validationError = when {
            quantityValue <= 0 -> stringResource(com.example.bookinghotel.R.string.quantity_min_error)
            quantityValue > currentRoom.availableRooms -> "Số lượng yêu cầu vượt quá số phòng sẵn có!"
            guestValue <= 0 -> "Số khách tối thiểu là 1."
            checkInDate < today -> "Ngày nhận phòng không được ở trong quá khứ."
            checkOutDate <= checkInDate -> "Ngày trả phòng phải sau ngày nhận phòng."
            else -> ""
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Xác nhận đặt phòng") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(currentRoom.type), fontWeight = FontWeight.Bold)
                        Text("${checkInDate.toDateLabel()} → ${checkOutDate.toDateLabel()} ($nights đêm)")
                        Text("$quantityValue phòng • $guestValue khách")
                        Text("Tổng dự kiến: ${estimatedTotal.toCurrencyLabel()}")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            viewModel.bookRoom(
                                quantity = quantityValue,
                                checkInDate = checkInDate,
                                checkOutDate = checkOutDate,
                                guests = guestValue
                            )
                        },
                        modifier = Modifier.testTag("confirm_booking_button")
                    ) {
                        Text("Xác nhận")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Kiểm tra lại")
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
                    Text(
                        text = stringResource(currentRoom.type),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentRoom.pricePerNight.toCurrencyLabel()}/đêm",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Tiện nghi: ${currentRoom.amenities.joinToString()}")
                    Text("Số phòng trống: ${currentRoom.availableRooms}")
                }
            }

            Text("Thời gian lưu trú", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showCheckInPicker = true },
                    modifier = Modifier.weight(1f).testTag("checkin_button")
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Nhận phòng", style = MaterialTheme.typography.labelSmall)
                        Text(checkInDate.toDateLabel())
                    }
                }
                OutlinedButton(
                    onClick = { showCheckOutPicker = true },
                    modifier = Modifier.weight(1f).testTag("checkout_button")
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Trả phòng", style = MaterialTheme.typography.labelSmall)
                        Text(checkOutDate.toDateLabel())
                    }
                }
            }

            Text("$nights đêm", color = MaterialTheme.colorScheme.secondary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Số phòng") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = quantityValue <= 0 || quantityValue > currentRoom.availableRooms,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quantity_input"),
                    enabled = bookingState !is BookingUiState.Loading
                )
                OutlinedTextField(
                    value = guests,
                    onValueChange = { guests = it },
                    label = { Text("Số khách") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = guestValue <= 0,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("guests_input"),
                    enabled = bookingState !is BookingUiState.Loading
                )
            }

            if (validationError.isNotEmpty()) {
                Text(
                    text = validationError,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("booking_validation_error")
                )
            }

            if (bookingState is BookingUiState.Error) {
                Text(
                    text = (bookingState as BookingUiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Chi phí dự kiến", fontWeight = FontWeight.Bold)
                    Text("${currentRoom.pricePerNight.toCurrencyLabel()} × $nights đêm × $quantityValue phòng")
                    Text(
                        text = estimatedTotal.toCurrencyLabel(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (bookingState is BookingUiState.Loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Button(
                onClick = { showConfirmDialog = true },
                enabled = validationError.isEmpty() && bookingState !is BookingUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("book_button")
            ) {
                Text("Đặt phòng • ${estimatedTotal.toCurrencyLabel()}")
            }

            OutlinedButton(
                onClick = { navController.popBackStack() },
                enabled = bookingState !is BookingUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Hủy")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingDatePickerDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val datePickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialDate
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let(onDateSelected)
                }
            ) {
                Text("Chọn")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun startOfToday(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun calculateNights(checkInDate: Long, checkOutDate: Long): Int {
    if (checkOutDate <= checkInDate) return 0
    return TimeUnit.MILLISECONDS.toDays(checkOutDate - checkInDate).toInt().coerceAtLeast(1)
}

private const val DAY_MS = 24L * 60L * 60L * 1000L
