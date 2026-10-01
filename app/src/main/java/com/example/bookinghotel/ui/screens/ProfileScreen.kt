package com.example.bookinghotel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookinghotel.data.auth.AuthSession

@Composable
fun ProfileScreen(session: AuthSession, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(session.user.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(session.user.email, style = MaterialTheme.typography.bodyLarge)
        Text("Vai trò: ${session.user.role}")
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Đăng xuất")
        }
    }
}
