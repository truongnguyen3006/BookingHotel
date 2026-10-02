package com.example.bookinghotel.ui.screens.admin

import androidx.compose.ui.res.stringResource
import com.example.bookinghotel.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookinghotel.data.AdminBooking
import com.example.bookinghotel.ui.AdminUiState
import com.example.bookinghotel.ui.toBookingStatusLabel
import com.example.bookinghotel.ui.toCurrencyLabel
import com.example.bookinghotel.ui.toDateLabel
import com.example.bookinghotel.ui.toDateTimeLabel
import com.example.bookinghotel.ui.toRoomLabel

@Composable
fun AdminBookingsScreen(
    state: AdminUiState,
    onRefresh: () -> Unit
) {
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
            Text(stringResource(R.string.tat_ca_booking), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onRefresh, enabled = !state.loadingBookings) {
                Text(stringResource(R.string.lam_moi))
            }
        }

        state.message?.takeIf { state.isError }?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (state.loadingBookings && state.bookings.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (!state.loadingBookings && state.bookings.isEmpty()) {
            Text(stringResource(R.string.admin_bookings_empty))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.bookings, key = { it.bookingId }) { booking ->
                AdminBookingCard(booking)
            }
        }
    }
}

@Composable
private fun AdminBookingCard(booking: AdminBooking) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(stringResource(R.string.booking, booking.bookingId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.khach, booking.userDisplayName ?: "--", booking.userEmail ?: "--"))
            Text(stringResource(R.string.phong, booking.room.typeKey.toRoomLabel(), booking.quantity))
            Text(stringResource(R.string.em_khach, booking.checkInDate.toDateLabel(), booking.checkOutDate.toDateLabel(), booking.nights, booking.guests))
            Text(stringResource(R.string.tong, booking.totalPrice.toCurrencyLabel()))
            Text(stringResource(R.string.booking_70d4e2, booking.status.toBookingStatusLabel()))
            Text(stringResource(R.string.tao_luc, booking.createdAt.toDateTimeLabel()), style = MaterialTheme.typography.bodySmall)

            val payment = booking.payment
            if (payment == null) {
                Text(stringResource(R.string.payment_chua_co_giao_dich), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.payment, payment.status, payment.method))
                payment.transactionId?.let { Text(stringResource(R.string.transaction, it), style = MaterialTheme.typography.bodySmall) }
                payment.paidAt?.let { Text(stringResource(R.string.paid_at, it.toDateTimeLabel()), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
