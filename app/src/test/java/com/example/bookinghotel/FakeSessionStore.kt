package com.example.bookinghotel

import com.example.bookinghotel.data.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeSessionStore(initial: AuthSession? = testSession(1)) : SessionStore {
    private val mutex = Mutex()
    override val session = MutableStateFlow(initial)
    var onClear: suspend () -> Unit = {}
    override suspend fun currentSession() = session.value
    override suspend fun save(session: AuthSession) = mutex.withLock { onClear(); this.session.value = session }
    override suspend fun clear() = mutex.withLock { onClear(); session.value = null }
    override suspend fun saveIfCurrent(expected: AuthSession, updated: AuthSession): AuthSession? = mutex.withLock {
        if (session.value?.sessionId != expected.sessionId || session.value?.refreshToken != expected.refreshToken) return@withLock null
        updated.copy(sessionId = expected.sessionId).also { session.value = it }
    }
    override suspend fun clearIfCurrent(expected: AuthSession, matchRefreshToken: Boolean) = mutex.withLock {
        if (session.value?.sessionId == expected.sessionId && (!matchRefreshToken || session.value?.refreshToken == expected.refreshToken)) {
            onClear(); session.value = null
        }
    }
    override suspend fun <T> withSession(expected: AuthSession, block: suspend () -> T): T = mutex.withLock {
        if (session.value?.sessionId != expected.sessionId) throw StaleSessionException()
        block()
    }
}

fun testSession(id: Long, access: String = "old-$id", refresh: String = "refresh-$id") =
    AuthSession(access, refresh, Long.MAX_VALUE, UserProfile(id, "user$id@test", "User $id", "USER"))
