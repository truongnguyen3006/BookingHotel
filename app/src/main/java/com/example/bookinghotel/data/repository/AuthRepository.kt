package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.auth.AuthSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<AuthSession?>
    suspend fun register(email: String, password: String, displayName: String): Result<AuthSession>
    suspend fun login(email: String, password: String): Result<AuthSession>
    suspend fun restoreSession(): Result<AuthSession?>
    suspend fun logout()
}
