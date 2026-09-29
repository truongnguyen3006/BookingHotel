package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen

@Composable
fun RoomListScreen(viewModel: BookingViewModel, navController: NavController) {
    val rooms = viewModel.rooms.collectAsState().value
    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 10.dp)

    ) {
        items(rooms) { room ->
            RoomItem(room = room) {
                viewModel.selectRoom(room)
                navController.navigate(Screen.Detail.route)
            }
        }
    }
}

@Composable
fun RoomItem(room: Room, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable {
                if (room.availableRooms > 0) {
                    onClick()
                }
            }
    ) {
        Column(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            ) {
            Image(
                painter = painterResource(room.image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Crop)

            Row {
                Text(text = "Loại phòng:")
                Text(text = stringResource(room.type), fontWeight = FontWeight.Bold)
            }
            Row {
                Text(text = "Giá mỗi đêm:")
                Text(text = "\$${room.pricePerNight}", fontWeight = FontWeight.Bold)
            }
            Row {
                Text(text = "Số phòng trống:")
                Text(text = "${room.availableRooms}", fontWeight = FontWeight.Bold)
            }
            if (room.availableRooms == 0) {
                Text(
                    text = "Hết phòng",
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun RoomListScreenPreview() {
    val mockViewModel = BookingViewModel().apply {
        rooms.collectAsState().value
    }
    RoomListScreen(viewModel = mockViewModel, navController = rememberNavController())
}
