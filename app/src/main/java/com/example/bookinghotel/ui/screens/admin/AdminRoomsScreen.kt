package com.example.bookinghotel.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bookinghotel.data.Room
import com.example.bookinghotel.ui.AdminUiState
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toRoomLabel

@Composable
fun AdminRoomsScreen(
    state: AdminUiState,
    onRefresh: () -> Unit,
    onUpdateRoom: (roomId: Int, pricePerNight: Long, availableRooms: Int) -> Unit,
    onClearMessage: () -> Unit
) {
    var editingRoom by remember { mutableStateOf<Room?>(null) }
    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kho phòng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onRefresh, enabled = !state.loadingRooms) {
                Text("Làm mới")
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        if (state.loadingRooms && state.rooms.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.rooms, key = { it.id }) { room ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(room.typeKey.toRoomLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Giá/đêm: ${room.pricePerNight.toCurrencyLabel()}")
                        Text("Còn lại: ${room.availableRooms} phòng")
                        Text("Tiện nghi: ${room.amenities.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                onClearMessage()
                                editingRoom = room
                            },
                            enabled = state.savingRoomId != room.id,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.savingRoomId == room.id) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Text("Sửa giá / số lượng")
                            }
                        }
                    }
                }
            }
        }
    }

    editingRoom?.let { room ->
        EditRoomDialog(
            room = room,
            onDismiss = { editingRoom = null },
            onSave = { price, available ->
                onUpdateRoom(room.id, price, available)
                editingRoom = null
            }
        )
    }
}

@Composable
private fun EditRoomDialog(
    room: Room,
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit
) {
    var priceText by remember(room.id) { mutableStateOf(room.pricePerNight.toString()) }
    var availableText by remember(room.id) { mutableStateOf(room.availableRooms.toString()) }
    val price = priceText.toLongOrNull()
    val available = availableText.toIntOrNull()
    val valid = price != null && price > 0L && available != null && available >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cập nhật ${room.typeKey.toRoomLabel()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Giá mỗi đêm (VND)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = availableText,
                    onValueChange = { availableText = it },
                    label = { Text("Số phòng còn lại") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(price!!, available!!) },
                enabled = valid
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
