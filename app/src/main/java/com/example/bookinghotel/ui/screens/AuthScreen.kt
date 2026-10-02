package com.example.bookinghotel.ui.screens

import androidx.compose.ui.res.stringResource
import com.example.bookinghotel.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.bookinghotel.ui.AuthUiState
import com.example.bookinghotel.ui.AuthViewModel

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val state by viewModel.uiState.collectAsState()
    val loading = state is AuthUiState.Loading

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (registerMode) stringResource(R.string.tao_tai_khoan) else stringResource(R.string.ang_nhap),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(20.dp))
        if (registerMode) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.ten_hien_thi)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("auth_display_name"),
                enabled = !loading
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_email"),
            enabled = !loading
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.mat_khau_toi_thieu_8_ky_tu)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_password"),
            enabled = !loading
        )
        val currentState = state
        if (currentState is AuthUiState.Error) {
            Spacer(Modifier.height(10.dp))
            Text(currentState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth_error"))
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                viewModel.clearError()
                if (registerMode) viewModel.register(email, password, displayName)
                else viewModel.login(email, password)
            },
            enabled = !loading && email.isNotBlank() && password.length >= 8 && (!registerMode || displayName.isNotBlank()),
            modifier = Modifier.fillMaxWidth().testTag("auth_submit")
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(if (registerMode) stringResource(R.string.ang_ky) else stringResource(R.string.ang_nhap))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { registerMode = !registerMode; viewModel.clearError() }, enabled = !loading, modifier = Modifier.testTag("auth_mode_toggle")) {
                Text(if (registerMode) stringResource(R.string.a_co_tai_khoan_ang_nhap) else stringResource(R.string.chua_co_tai_khoan_ang_ky))
            }
        }
    }
}
