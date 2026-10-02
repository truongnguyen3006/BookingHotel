package com.example.bookinghotel.ui.screens.admin

import androidx.compose.ui.res.stringResource
import com.example.bookinghotel.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.bookinghotel.ui.AdminUiState
import com.example.bookinghotel.ui.toCurrencyLabel

@Composable
fun AdminDashboardScreen(
    state: AdminUiState,
    onRefresh: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenBookings: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.tong_quan_quan_tri),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (state.loadingDashboard && state.dashboard == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            state.dashboard?.let { dashboard ->
                DashboardMetric(stringResource(R.string.tong_booking), dashboard.totalBookings.toString())
                DashboardMetric(stringResource(R.string.booking_a_thanh_toan), dashboard.paidBookings.toString())
                DashboardMetric(stringResource(R.string.doanh_thu), dashboard.totalRevenue.toCurrencyLabel())
                DashboardMetric(stringResource(R.string.loai_phong), dashboard.roomTypes.toString())
                DashboardMetric(stringResource(R.string.phong_con_lai), dashboard.availableRoomInventory.toString())
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        Button(onClick = onOpenRooms, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quan_ly_phong))
        }
        Button(onClick = onOpenBookings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.quan_ly_booking_payment))
        }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lam_moi_dashboard))
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
