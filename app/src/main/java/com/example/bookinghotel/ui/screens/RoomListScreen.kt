package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.bookinghotel.R
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen

@Composable
fun RoomListScreen(viewModel: BookingViewModel, navController: NavController) {
    val rooms = viewModel.rooms.collectAsState().value
    val isLoading = viewModel.isLoadingRooms.collectAsState().value
    val loadError = viewModel.roomLoadError.collectAsState().value

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && rooms.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            loadError != null && rooms.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = loadError, color = Color.Red)
                    Button(onClick = viewModel::loadRooms) {
                        Text("Thử lại")
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp)
                ) {
                    items(rooms, key = { it.id }) { room ->
                        RoomItem(room = room) {
                            viewModel.selectRoom(room)
                            navController.navigate(Screen.Detail.route)
                        }
                    }
                }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(room.image),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Crop
            )

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
fun RoomItemPreview() {
    RoomItem(
        room = Room(
            id = 1,
            image = R.drawable.standard_room,
            type = R.string.room_style_1,
            pricePerNight = 50.0,
            amenities = listOf("Wi-Fi", "TV"),
            availableRooms = 10
        ),
        onClick = {}
    )
}
