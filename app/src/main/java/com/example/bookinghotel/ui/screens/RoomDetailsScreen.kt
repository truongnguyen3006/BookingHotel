package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.ui.BookingUiState
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen

@Composable
fun RoomDetailsScreen(viewModel: BookingViewModel, navController: NavController) {
    val room = viewModel.selectedRoom.collectAsState().value
    val bookingState = viewModel.bookingState.collectAsState().value

    var quantity by rememberSaveable { mutableStateOf("1") }
    var tempQuantity by rememberSaveable { mutableStateOf(1) }
    var validationError by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(bookingState) {
        if (bookingState is BookingUiState.Success) {
            navController.navigate(Screen.Summary.route)
            viewModel.consumeBookingSuccess()
        }
    }

    room?.let {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp)
            ) {
                Text(text = "Loại phòng: ${stringResource(room.type)}")
                Text(text = "Giá mỗi đêm: \$${room.pricePerNight}")
                Text(text = "Tiện nghi: ${room.amenities.joinToString()}")
                Text(text = "Số phòng trống: ${room.availableRooms}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = quantity,
                onValueChange = { input ->
                    quantity = input
                    tempQuantity = input.toIntOrNull() ?: 0
                    validationError = when {
                        tempQuantity > room.availableRooms -> {
                            "Số lượng yêu cầu vượt quá số phòng sẵn có!"
                        }

                        tempQuantity <= 0 -> {
                            "Số lượng đặt phòng tối thiểu là 1."
                        }

                        else -> ""
                    }
                },
                label = { Text("Số lượng phòng") },
                isError = validationError.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("quantity_input"),
                enabled = bookingState !is BookingUiState.Loading
            )

            if (validationError.isNotEmpty()) {
                Text(
                    text = validationError,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (bookingState is BookingUiState.Error) {
                Text(
                    text = bookingState.message,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (bookingState is BookingUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (tempQuantity > 0 && tempQuantity <= room.availableRooms) {
                            viewModel.bookRoom(tempQuantity)
                        }
                    },
                    enabled = validationError.isEmpty() &&
                        bookingState !is BookingUiState.Loading,
                    modifier = Modifier
                        .width(150.dp)
                        .testTag("book_button")
                ) {
                    Text("Đặt phòng")
                }

                Button(
                    onClick = { navController.popBackStack() },
                    enabled = bookingState !is BookingUiState.Loading,
                    modifier = Modifier.width(150.dp)
                ) {
                    Text("Hủy")
                }
            }
        }
    }
}
