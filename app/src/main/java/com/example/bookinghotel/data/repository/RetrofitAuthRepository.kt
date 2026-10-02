package com.example.bookinghotel.data.repository

import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.auth.SessionStore
import com.example.bookinghotel.data.remote.TokenRefreshCoordinator
import com.example.bookinghotel.data.auth.UserProfile
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
    private val tokenStore: SessionStore,
    private val refreshCoordinator: TokenRefreshCoordinator
) : AuthRepository {
    override val session: Flow<AuthSession?> = tokenStore.session

    override suspend fun register(email: String, password: String, displayName: String): Result<AuthSession> = runCatching {
        val response = api.register(RegisterRequestDto(email.trim(), password, displayName.trim()))
        response.persist()
    }

    override suspend fun login(email: String, password: String): Result<AuthSession> = runCatching {
        val response = api.login(LoginRequestDto(email.trim(), password))
        response.persist()
    }

    override suspend fun restoreSession(): Result<AuthSession?> {
        tokenStore.resumeSync()
        val current = tokenStore.currentSession() ?: return Result.success(null)
        if (current.accessTokenExpiresAt > System.currentTimeMillis() + 30_000L) {
            return Result.success(current)
        }
        return runCatching {
            refreshCoordinator.refreshSingleFlight(current.accessToken,
                currentValue = { tokenStore.currentSession()?.takeIf { it.sessionId == current.sessionId } },
                accessTokenOf = AuthSession::accessToken
            ) { latest ->
                try {
                    tokenStore.saveIfCurrent(latest, api.refresh(RefreshTokenRequestDto(latest.refreshToken)).toSession())
                } catch (exception: HttpException) {
                    if (exception.code() in listOf(401, 403)) tokenStore.clearIfCurrent(latest)
                    throw exception
                }
            }
        }
    }

    override suspend fun logout() {
        val current = tokenStore.currentSession()
        // Invalidate locally before a slow/offline server revoke request.
        if (current != null) tokenStore.clearIfCurrent(current, matchRefreshToken = false)
        if (current != null) runCatching { api.logout(RefreshTokenRequestDto(current.refreshToken)) }
    }

    private suspend fun AuthResponseDto.persist(): AuthSession {
        val session = toSession()
        tokenStore.save(session)
        return session
    }

    private fun AuthResponseDto.toSession(): AuthSession = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L,
            user = UserProfile(user.id, user.email, user.displayName, user.role)
        )
}
