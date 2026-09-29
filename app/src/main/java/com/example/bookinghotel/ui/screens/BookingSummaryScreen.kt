package com.example.bookinghotel.ui.screens


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen

@Composable
fun BookingSummaryScreen(viewModel: BookingViewModel, navController: NavController) {
    val room = viewModel.selectedRoom.collectAsState().value
    val quantity = viewModel.quantity.collectAsState().value

    room?.let {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Tóm tắt đặt phòng", textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(30.dp))
            Text(text = "Loại phòng: ${stringResource(room.type)}")
            Text(text = "Tiện nghi: ${room.amenities.joinToString()}")
            Text(text = "Số lượng phòng đã đặt: ${quantity}")
            Text(text = "Tổng tiền: \$${room.pricePerNight * quantity}")
            Spacer(modifier = Modifier.height(16.dp))
            Text("Phòng đã được đặt. Bạn có 3 giờ để thanh toán!!.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack(Screen.List.route, inclusive = false) },
                modifier = Modifier.align(alignment = Alignment.CenterHorizontally)) {
                Text("Quay về màn hình chính")
            }
        }
    }
}
