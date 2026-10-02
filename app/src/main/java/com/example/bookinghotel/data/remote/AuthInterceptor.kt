package com.example.bookinghotel.data.remote

import com.example.bookinghotel.data.auth.SessionStore
import com.example.bookinghotel.data.auth.RequestSession
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor @Inject constructor(
    private val tokenStore: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking { tokenStore.currentSession() }
        val token = session?.accessToken
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .tag(RequestSession::class.java, RequestSession(session.sessionId))
                .build()
        }
        return chain.proceed(request)
    }
}
