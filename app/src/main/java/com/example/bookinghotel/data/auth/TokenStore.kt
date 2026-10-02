package com.example.bookinghotel.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.bookinghotel.data.local.BookingDao
import com.example.bookinghotel.background.BackgroundSyncScheduler

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookingDao: BookingDao
) : SessionStore {
    private val sessionMutex = Mutex()

    override val session: Flow<AuthSession?> = context.authDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(::toSession)

    override suspend fun currentSession(): AuthSession? = session.first()

    override suspend fun save(session: AuthSession) = sessionMutex.withLock {
        BackgroundSyncScheduler.cancelAuthenticatedSync(context)
        bookingDao.clearBookings()
        persist(session)
        if (!session.user.role.equals("ADMIN", ignoreCase = true)) {
            BackgroundSyncScheduler.scheduleAuthenticatedSync(context)
        }
    }

    override suspend fun clear() = sessionMutex.withLock { clearLocked() }

    override suspend fun saveIfCurrent(expected: AuthSession, updated: AuthSession): AuthSession? = sessionMutex.withLock {
        val latest = currentSession()
        if (latest?.sessionId != expected.sessionId || latest.refreshToken != expected.refreshToken) return@withLock null
        if (updated.user.id != expected.user.id) throw StaleSessionException()
        updated.copy(sessionId = expected.sessionId).also { persist(it) }
    }

    override suspend fun clearIfCurrent(expected: AuthSession, matchRefreshToken: Boolean) = sessionMutex.withLock {
        val latest = currentSession()
        if (latest?.sessionId == expected.sessionId && (!matchRefreshToken || latest.refreshToken == expected.refreshToken)) clearLocked()
    }

    override suspend fun <T> withSession(expected: AuthSession, block: suspend () -> T): T = sessionMutex.withLock {
        if (currentSession()?.sessionId != expected.sessionId) throw StaleSessionException()
        block()
    }

    override suspend fun resumeSync() = sessionMutex.withLock {
        val session = currentSession()
        if (session != null && !session.user.role.equals("ADMIN", ignoreCase = true)) {
            BackgroundSyncScheduler.scheduleAuthenticatedSync(context)
        }
    }

    private suspend fun clearLocked() {
        BackgroundSyncScheduler.cancelAuthenticatedSync(context)
        // Clear before emitting logout; the same lock prevents late writes after this.
        bookingDao.clearBookings()
        context.authDataStore.edit { it.clear() }
    }

    private suspend fun persist(session: AuthSession) {
        context.authDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[REFRESH_TOKEN] = session.refreshToken
            preferences[ACCESS_EXPIRES_AT] = session.accessTokenExpiresAt
            preferences[USER_ID] = session.user.id
            preferences[USER_EMAIL] = session.user.email
            preferences[USER_NAME] = session.user.displayName
            preferences[USER_ROLE] = session.user.role
            preferences[SESSION_ID] = session.sessionId
        }
    }

    fun getAccessTokenBlocking(): String? = runBlocking { currentSession()?.accessToken }

    private fun toSession(preferences: Preferences): AuthSession? {
        val access = preferences[ACCESS_TOKEN] ?: return null
        val refresh = preferences[REFRESH_TOKEN] ?: return null
        val userId = preferences[USER_ID] ?: return null
        val email = preferences[USER_EMAIL] ?: return null
        val name = preferences[USER_NAME] ?: return null
        val role = preferences[USER_ROLE] ?: return null
        val expiresAt = preferences[ACCESS_EXPIRES_AT] ?: 0L
        return AuthSession(access, refresh, expiresAt, UserProfile(userId, email, name, role),
            preferences[SESSION_ID] ?: UUID.nameUUIDFromBytes("$userId:$refresh".toByteArray()).toString())
    }

    companion object {
        private val SESSION_ID = stringPreferencesKey("session_id")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
        private val USER_ID = longPreferencesKey("user_id")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_ROLE = stringPreferencesKey("user_role")
    }
}
