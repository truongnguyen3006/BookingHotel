package com.example.bookinghotel

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.repository.AuthRepository
import com.example.bookinghotel.ui.AndroidAppStrings
import com.example.bookinghotel.ui.AuthViewModel
import com.example.bookinghotel.ui.screens.AuthScreen
import com.example.bookinghotel.ui.theme.BookingHotelTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AuthFlowUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loginValidationAndServerError_useStableSemantics() {
        val repository = object : AuthRepository {
            override val session = MutableStateFlow<AuthSession?>(null)
            override suspend fun restoreSession() = Result.success<AuthSession?>(null)
            override suspend fun login(email: String,password: String) = Result.failure<AuthSession>(IllegalArgumentException("Test login error"))
            override suspend fun register(email: String,password: String,displayName: String) = Result.failure<AuthSession>(IllegalArgumentException("Test registration error"))
            override suspend fun logout() = Unit
        }
        val vm = AuthViewModel(repository, AndroidAppStrings(ApplicationProvider.getApplicationContext()))
        compose.setContent { BookingHotelTheme { AuthScreen(vm) } }
        compose.onNodeWithTag("auth_submit").assertIsNotEnabled()
        compose.onNodeWithTag("auth_email").performTextInput("a@test.com")
        compose.onNodeWithTag("auth_password").performTextInput("password123")
        compose.onNodeWithTag("auth_submit").performClick()
        compose.onNodeWithTag("auth_error").assertIsDisplayed()
        compose.onNodeWithTag("auth_mode_toggle").performClick()
        compose.onNodeWithTag("auth_display_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_submit").assertIsNotEnabled()
        compose.onNodeWithTag("auth_display_name").performTextInput("Test")
        compose.onNodeWithTag("auth_submit").assertIsEnabled()
    }
}
