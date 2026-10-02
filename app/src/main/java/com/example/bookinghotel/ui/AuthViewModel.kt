package com.example.bookinghotel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.remote.toAppError
import com.example.bookinghotel.data.remote.userMessage
import com.example.bookinghotel.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val strings: AppStrings
) : ViewModel() {
    val session: StateFlow<AuthSession?> = authRepository.session.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.restoreSession()
                .onFailure { throwable ->
                    _uiState.value = AuthUiState.Error(throwable.toAppError().userMessage(strings))
                }
            if (_uiState.value is AuthUiState.Loading) _uiState.value = AuthUiState.Idle
        }
    }

    fun login(email: String, password: String) = submit {
        authRepository.login(email, password)
    }

    fun register(email: String, password: String, displayName: String) = submit {
        authRepository.register(email, password, displayName)
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.logout()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) _uiState.value = AuthUiState.Idle
    }

    private fun submit(block: suspend () -> Result<AuthSession>) {
        if (_uiState.value is AuthUiState.Loading) return
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            block()
                .onSuccess { _uiState.value = AuthUiState.Idle }
                .onFailure { _uiState.value = AuthUiState.Error(it.toAppError().userMessage(strings)) }
        }
    }
}
