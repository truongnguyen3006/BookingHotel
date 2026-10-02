package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookinghotel.R
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.data.repository.RoomDataSource
import com.example.bookinghotel.ui.PriceFilter
import com.example.bookinghotel.ui.RoomSortOption
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.toCurrencyLabel

@Composable
fun RoomListScreen(viewModel: BookingViewModel, navController: NavController) {
    val rooms by viewModel.filteredRooms.collectAsState()
    val sourceRooms by viewModel.rooms.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val isLoading by viewModel.isLoadingRooms.collectAsState()
    val loadError by viewModel.roomLoadError.collectAsState()
    val roomDataSource by viewModel.roomDataSource.collectAsState()
    val lastRoomSyncAt by viewModel.lastRoomSyncAt.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(loadError, sourceRooms.isNotEmpty()) {
        if (loadError != null && sourceRooms.isNotEmpty()) {
            snackbarHostState.showSnackbar(loadError.orEmpty())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filterState.query,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("room_search"),
                    singleLine = true,
                    label = { Text("Tìm loại phòng hoặc tiện nghi") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )

                OutlinedButton(
                    onClick = { navController.navigate(Screen.History.route) },
                    modifier = Modifier.testTag("history_button")
                ) {
                    Text("Lịch sử")
                }
            }

            Text(
                text = "Bộ lọc",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterState.onlyAvailable,
                    onClick = viewModel::toggleAvailableOnly,
                    label = { Text("Còn phòng") }
                )
                FilterChip(
                    selected = filterState.priceFilter == PriceFilter.UNDER_2_5_MILLION,
                    onClick = {
                        viewModel.setPriceFilter(
                            if (filterState.priceFilter == PriceFilter.UNDER_2_5_MILLION) {
                                PriceFilter.ALL
                            } else {
                                PriceFilter.UNDER_2_5_MILLION
                            }
                        )
                    },
                    label = { Text("Dưới 2,5 triệu") }
                )
                FilterChip(
                    selected = filterState.priceFilter == PriceFilter.AT_LEAST_2_5_MILLION,
                    onClick = {
                        viewModel.setPriceFilter(
                            if (filterState.priceFilter == PriceFilter.AT_LEAST_2_5_MILLION) {
                                PriceFilter.ALL
                            } else {
                                PriceFilter.AT_LEAST_2_5_MILLION
                            }
                        )
                    },
                    label = { Text("Từ 2,5 triệu") }
                )
                listOf("Wi-Fi", "Breakfast", "Jacuzzi").forEach { amenity ->
                    FilterChip(
                        selected = filterState.amenity == amenity,
                        onClick = {
                            viewModel.setAmenityFilter(
                                if (filterState.amenity == amenity) null else amenity
                            )
                        },
                        label = { Text(amenity) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterState.sortOption == RoomSortOption.PRICE_LOW_TO_HIGH,
                    onClick = {
                        viewModel.setSortOption(
                            if (filterState.sortOption == RoomSortOption.PRICE_LOW_TO_HIGH) {
                                RoomSortOption.DEFAULT
                            } else {
                                RoomSortOption.PRICE_LOW_TO_HIGH
                            }
                        )
                    },
                    label = { Text("Giá thấp → cao") }
                )
                FilterChip(
                    selected = filterState.sortOption == RoomSortOption.PRICE_HIGH_TO_LOW,
                    onClick = {
                        viewModel.setSortOption(
                            if (filterState.sortOption == RoomSortOption.PRICE_HIGH_TO_LOW) {
                                RoomSortOption.DEFAULT
                            } else {
                                RoomSortOption.PRICE_HIGH_TO_LOW
                            }
                        )
                    },
                    label = { Text("Giá cao → thấp") }
                )
                if (filterState.query.isNotBlank() ||
                    filterState.onlyAvailable ||
                    filterState.priceFilter != PriceFilter.ALL ||
                    filterState.amenity != null ||
                    filterState.sortOption != RoomSortOption.DEFAULT
                ) {
                    AssistChip(
                        onClick = viewModel::clearRoomFilters,
                        label = { Text("Xóa bộ lọc") }
                    )
                }
            }

            if (roomDataSource == RoomDataSource.CACHE && sourceRooms.isNotEmpty()) {
                AssistChip(
                    onClick = viewModel::loadRooms,
                    label = {
                        Text(
                            if (lastRoomSyncAt != null) {
                                "Đang dùng dữ liệu đã lưu • chạm để đồng bộ lại"
                            } else {
                                "Đang dùng dữ liệu đã lưu"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading && sourceRooms.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Đang tải danh sách phòng...")
                        }
                    }

                    loadError != null && sourceRooms.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = loadError.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(onClick = viewModel::loadRooms) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text(" Thử lại")
                            }
                        }
                    }

                    rooms.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Không có phòng phù hợp với bộ lọc hiện tại.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            OutlinedButton(onClick = viewModel::clearRoomFilters) {
                                Text("Xóa bộ lọc")
                            }
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
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
    }
}

@Composable
fun RoomItem(room: Room, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("room_card_${room.id}")
            .clickable(enabled = room.availableRooms > 0, onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(room.image),
                contentDescription = stringResource(room.type),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(room.type),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${room.pricePerNight.toCurrencyLabel()}/đêm",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (room.availableRooms > 0) {
                        "Còn ${room.availableRooms} phòng"
                    } else {
                        "Hết phòng"
                    },
                    color = if (room.availableRooms > 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    room.amenities.take(4).forEach { amenity ->
                        AssistChip(
                            onClick = {},
                            label = { Text(amenity) }
                        )
                    }
                }
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
            typeKey = "standard",
            pricePerNight = 1_250_000L,
            amenities = listOf("Wi-Fi", "TV"),
            availableRooms = 10
        ),
        onClick = {}
    )
}
