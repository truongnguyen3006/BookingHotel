package com.example.bookinghotel.data.auth

import kotlinx.coroutines.flow.Flow

/** All private-cache writes share the same critical section as login/logout/refresh. */
interface SessionStore {
    val session: Flow<AuthSession?>
    suspend fun resumeSync() {}
    suspend fun currentSession(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear()
    suspend fun saveIfCurrent(expected: AuthSession, updated: AuthSession): AuthSession?
    suspend fun clearIfCurrent(expected: AuthSession, matchRefreshToken: Boolean = true)
    suspend fun <T> withSession(expected: AuthSession, block: suspend () -> T): T
}

class StaleSessionException : IllegalStateException("Authenticated session changed; response discarded")

data class RequestSession(val sessionId: String)
