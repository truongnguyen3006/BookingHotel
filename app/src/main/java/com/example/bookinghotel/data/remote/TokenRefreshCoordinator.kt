package com.example.bookinghotel.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes token refresh attempts and, more importantly, lets callers reuse a token that
 * another request already refreshed while they were waiting for the mutex.
 */
@Singleton
class TokenRefreshCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> refreshSingleFlight(
        failedAccessToken: String?,
        currentValue: suspend () -> T?,
        accessTokenOf: (T) -> String,
        refresh: suspend (T) -> T?
    ): T? = mutex.withLock {
        val latest = currentValue() ?: return@withLock null
        if (!failedAccessToken.isNullOrBlank() && accessTokenOf(latest) != failedAccessToken) {
            return@withLock latest
        }
        refresh(latest)
    }
}
