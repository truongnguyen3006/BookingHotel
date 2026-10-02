package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.auth.SessionStore
import com.example.bookinghotel.data.auth.RequestSession
import com.example.bookinghotel.data.auth.UserProfile
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
    private val tokenStore: SessionStore,
    private val refreshCoordinator: TokenRefreshCoordinator
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val failedAccessToken = response.request()
            .header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val requestSession = response.request().tag(RequestSession::class.java) ?: return null
        val session = runBlocking {
            if (tokenStore.currentSession()?.sessionId != requestSession.sessionId) return@runBlocking null
            refreshCoordinator.refreshSingleFlight(
                failedAccessToken = failedAccessToken,
                currentValue = { tokenStore.currentSession()?.takeIf { it.sessionId == requestSession.sessionId } },
                accessTokenOf = AuthSession::accessToken
            ) { current ->
                try {
                    val refreshed = authApi.refresh(RefreshTokenRequestDto(current.refreshToken))
                    val updated = AuthSession(
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        accessTokenExpiresAt = System.currentTimeMillis() + refreshed.expiresInSeconds * 1000L,
                        user = UserProfile(
                            refreshed.user.id,
                            refreshed.user.email,
                            refreshed.user.displayName,
                            refreshed.user.role
                        )
                    )
                    tokenStore.saveIfCurrent(current, updated)
                } catch (throwable: Exception) {
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    if (throwable is HttpException && throwable.code() in listOf(401, 403)) {
                        // A rotated/revoked refresh token means this session is no longer valid.
                        // Clear both auth state and user-specific cache immediately.
                        tokenStore.clearIfCurrent(current)
                    }
                    null
                }
            }
        } ?: return null

        if (runBlocking { tokenStore.currentSession()?.sessionId } != requestSession.sessionId) return null
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
