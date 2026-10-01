package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.data.auth.TokenStore
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
    private val tokenStore: TokenStore
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val current = runBlocking { tokenStore.currentSession() } ?: return null

        val refreshResult = runBlocking {
            runCatching { authApi.refresh(RefreshTokenRequestDto(current.refreshToken)) }
        }
        val refreshed = refreshResult.getOrElse { throwable ->
            if (throwable is HttpException && throwable.code() == 401) {
                runBlocking { tokenStore.clear() }
            }
            return null
        }

        val newSession = AuthSession(
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
        runBlocking { tokenStore.save(newSession) }

        return response.request().newBuilder()
            .header("Authorization", "Bearer ${newSession.accessToken}")
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
