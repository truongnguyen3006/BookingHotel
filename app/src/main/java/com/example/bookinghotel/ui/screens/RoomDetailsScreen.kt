package com.example.bookinghotel.ui.screens

import com.example.bookinghotel.R
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
            showConfirmDialog = false
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

    if (room == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(com.example.bookinghotel.R.string.room_missing))
            Button(onClick = { navController.popBackStack() }) { Text(stringResource(com.example.bookinghotel.R.string.back)) }
        }
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
            quantityValue > currentRoom.availableRooms -> stringResource(R.string.so_luong_yeu_cau_vuot_qua_so_phong_san_co)
            guestValue <= 0 -> stringResource(R.string.so_khach_toi_thieu_la_1)
            checkInDate < today -> stringResource(R.string.ngay_nhan_phong_khong_uoc_o_trong_qua_khu)
            checkOutDate <= checkInDate -> stringResource(R.string.ngay_tra_phong_phai_sau_ngay_nhan_phong)
            else -> ""
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { if (bookingState !is BookingUiState.Loading) showConfirmDialog = false },
                title = { Text(stringResource(R.string.xac_nhan_at_phong)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(currentRoom.type), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.em_bec65d, checkInDate.toDateLabel(), checkOutDate.toDateLabel(), nights))
                        Text(stringResource(R.string.phong_khach_64f8d0, quantityValue, guestValue))
                        Text(stringResource(R.string.tong_du_kien, estimatedTotal.toCurrencyLabel()))
                        if (bookingState is BookingUiState.Error) {
                            Text((bookingState as BookingUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("booking_confirmation_error"))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.bookRoom(
                                quantity = quantityValue,
                                checkInDate = checkInDate,
                                checkOutDate = checkOutDate,
                                guests = guestValue
                            )
                        },
                        enabled = bookingState !is BookingUiState.Loading && validationError.isEmpty(),
                        modifier = Modifier.testTag("confirm_booking_button")
                    ) {
                        if (bookingState is BookingUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.testTag("booking_confirmation_loading"))
                        } else Text(stringResource(R.string.xac_nhan))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }, enabled = bookingState !is BookingUiState.Loading) {
                        Text(stringResource(R.string.kiem_tra_lai))
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
                        text = stringResource(R.string.em, currentRoom.pricePerNight.toCurrencyLabel()),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.tien_nghi, currentRoom.amenities.joinToString()))
                    Text(stringResource(R.string.so_phong_trong, currentRoom.availableRooms))
                }
            }

            Text(stringResource(R.string.thoi_gian_luu_tru), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    enabled = bookingState !is BookingUiState.Loading,
                    onClick = { showCheckInPicker = true },
                    modifier = Modifier.weight(1f).testTag("checkin_button")
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(stringResource(R.string.nhan_phong), style = MaterialTheme.typography.labelSmall)
                        Text(checkInDate.toDateLabel())
                    }
                }
                OutlinedButton(
                    enabled = bookingState !is BookingUiState.Loading,
                    onClick = { showCheckOutPicker = true },
                    modifier = Modifier.weight(1f).testTag("checkout_button")
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(stringResource(R.string.tra_phong), style = MaterialTheme.typography.labelSmall)
                        Text(checkOutDate.toDateLabel())
                    }
                }
            }

            Text(stringResource(R.string.em_b7c0ce, nights), color = MaterialTheme.colorScheme.secondary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.so_phong)) },
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
                    label = { Text(stringResource(R.string.so_khach)) },
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("booking_error")
                )
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.chi_phi_du_kien), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.em_phong, currentRoom.pricePerNight.toCurrencyLabel(), nights, quantityValue))
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
                Text(stringResource(R.string.at_phong, estimatedTotal.toCurrencyLabel()))
            }

            OutlinedButton(
                onClick = { navController.popBackStack() },
                enabled = bookingState !is BookingUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.huy))
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
                Text(stringResource(R.string.chon))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.huy))
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
