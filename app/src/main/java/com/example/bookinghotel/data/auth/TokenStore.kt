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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var cachedAccessToken: String? = null

    val session: Flow<AuthSession?> = context.authDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(::toSession)

    suspend fun currentSession(): AuthSession? = session.first()

    suspend fun save(session: AuthSession) {
        cachedAccessToken = session.accessToken
        context.authDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[REFRESH_TOKEN] = session.refreshToken
            preferences[ACCESS_EXPIRES_AT] = session.accessTokenExpiresAt
            preferences[USER_ID] = session.user.id
            preferences[USER_EMAIL] = session.user.email
            preferences[USER_NAME] = session.user.displayName
            preferences[USER_ROLE] = session.user.role
        }
    }

    suspend fun clear() {
        cachedAccessToken = null
        context.authDataStore.edit { it.clear() }
    }

    fun getAccessTokenBlocking(): String? {
        cachedAccessToken?.let { return it }
        val token = runBlocking { currentSession()?.accessToken }
        cachedAccessToken = token
        return token
    }

    private fun toSession(preferences: Preferences): AuthSession? {
        val access = preferences[ACCESS_TOKEN] ?: return null
        val refresh = preferences[REFRESH_TOKEN] ?: return null
        val userId = preferences[USER_ID] ?: return null
        val email = preferences[USER_EMAIL] ?: return null
        val name = preferences[USER_NAME] ?: return null
        val role = preferences[USER_ROLE] ?: return null
        val expiresAt = preferences[ACCESS_EXPIRES_AT] ?: 0L
        cachedAccessToken = access
        return AuthSession(access, refresh, expiresAt, UserProfile(userId, email, name, role))
    }

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
        private val USER_ID = longPreferencesKey("user_id")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_ROLE = stringPreferencesKey("user_role")
    }
}
