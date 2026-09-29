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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen

@Composable
fun RoomDetailsScreen(viewModel: BookingViewModel, navController: NavController) {
    val room = viewModel.selectedRoom.collectAsState().value
    var quantity by rememberSaveable  { mutableStateOf("1") }
    var temp_quantity by rememberSaveable { mutableStateOf(1) }
    var error by rememberSaveable { mutableStateOf("") }
    room?.let {
        Column(modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 50.dp),
            ) {
                Text(text = "Loại phòng: ${stringResource(room.type)}")
                Text(text = "Giá mỗi đêm: \$${room.pricePerNight}")
                Text(text = "Tiện nghi: ${room.amenities.joinToString()}")
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    quantity = it
                    temp_quantity = (quantity.toIntOrNull() ?:0)
                    error = if (temp_quantity > room.availableRooms) {
                        "Số lượng yêu cầu vượt quá số phòng sẵn có!"
                    } else if(temp_quantity <= 0){
                        "Số lượng đặt phòng tối thiểu là 1."
                    }else ""
                },
                label = { Text("Số lượng phòng") },
                isError = error.isNotEmpty(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (error.isNotEmpty()) {
                Text(text = error, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    if (temp_quantity > 0 && temp_quantity <= room.availableRooms) {
                        viewModel.setQuantity(temp_quantity)
                        viewModel.bookRoom(temp_quantity)
                        navController.navigate(Screen.Summary.route)
                    }
                },
                    enabled = error.isEmpty(),
                    modifier = Modifier
                        .width(150.dp)
                ) {
                    Text("Đặt phòng")
                }
                Button(onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .width(150.dp)

                ) {
                    Text("Hủy")
                }
            }
        }
    }
}


