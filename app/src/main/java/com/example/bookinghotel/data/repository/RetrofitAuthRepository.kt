package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.auth.TokenStore
import com.example.bookinghotel.data.auth.UserProfile
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.remote.AuthApiService
import com.example.bookinghotel.data.remote.dto.AuthResponseDto
import com.example.bookinghotel.data.remote.dto.LoginRequestDto
import com.example.bookinghotel.data.remote.dto.RefreshTokenRequestDto
import com.example.bookinghotel.data.remote.dto.RegisterRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

@Singleton
class RetrofitAuthRepository @Inject constructor(
    private val api: AuthApiService,
    private val tokenStore: TokenStore,
    private val bookingDao: BookingDao
) : AuthRepository {
    override val session: Flow<AuthSession?> = tokenStore.session

    override suspend fun register(email: String, password: String, displayName: String): Result<AuthSession> = runCatching {
        val response = api.register(RegisterRequestDto(email.trim(), password, displayName.trim()))
        bookingDao.clearBookings()
        response.persist()
    }

    override suspend fun login(email: String, password: String): Result<AuthSession> = runCatching {
        val response = api.login(LoginRequestDto(email.trim(), password))
        bookingDao.clearBookings()
        response.persist()
    }

    override suspend fun restoreSession(): Result<AuthSession?> {
        val current = tokenStore.currentSession() ?: return Result.success(null)
        if (current.accessTokenExpiresAt > System.currentTimeMillis() + 30_000L) {
            return Result.success(current)
        }
        return runCatching {
            api.refresh(RefreshTokenRequestDto(current.refreshToken)).persist()
        }.onFailure { throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                tokenStore.clear()
                bookingDao.clearBookings()
            }
        }
    }

    override suspend fun logout() {
        val current = tokenStore.currentSession()
        if (current != null) {
            runCatching { api.logout(RefreshTokenRequestDto(current.refreshToken)) }
        }
        tokenStore.clear()
        bookingDao.clearBookings()
    }

    private suspend fun AuthResponseDto.persist(): AuthSession {
        val session = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L,
            user = UserProfile(user.id, user.email, user.displayName, user.role)
        )
        tokenStore.save(session)
        return session
    }
}
