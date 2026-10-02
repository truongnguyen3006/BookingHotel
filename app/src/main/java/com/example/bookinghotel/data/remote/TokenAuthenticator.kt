package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.auth.TokenStore
import com.example.bookinghotel.data.auth.UserProfile
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.data.remote.dto.RefreshTokenRequestDto
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

class TokenAuthenticator @Inject constructor(
    private val authApi: AuthApiService,
    private val tokenStore: TokenStore,
    private val bookingDao: BookingDao,
    private val refreshCoordinator: TokenRefreshCoordinator
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val failedAccessToken = response.request()
            .header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val session = runBlocking {
            refreshCoordinator.refreshSingleFlight(
                failedAccessToken = failedAccessToken,
                currentValue = tokenStore::currentSession,
                accessTokenOf = AuthSession::accessToken
            ) { current ->
                try {
                    val refreshed = authApi.refresh(RefreshTokenRequestDto(current.refreshToken))
                    AuthSession(
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        accessTokenExpiresAt = System.currentTimeMillis() + refreshed.expiresInSeconds * 1000L,
                        user = UserProfile(
                            refreshed.user.id,
                            refreshed.user.email,
                            refreshed.user.displayName,
                            refreshed.user.role
                        )
                    ).also { tokenStore.save(it) }
                } catch (throwable: Throwable) {
                    if (throwable is HttpException && throwable.code() == 401) {
                        // A rotated/revoked refresh token means this session is no longer valid.
                        // Clear both auth state and user-specific cache immediately.
                        tokenStore.clear()
                        bookingDao.clearBookings()
                    }
                    null
                }
            }
        } ?: return null

        return response.request().newBuilder()
            .header("Authorization", "Bearer ${session.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse()
        while (prior != null) {
            count++
            prior = prior.priorResponse()
        }
        return count
    }
}
