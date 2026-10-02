package com.example.bookinghotel.data.remote

import com.example.bookinghotel.FakeSessionStore
import com.example.bookinghotel.testSession
import com.example.bookinghotel.data.auth.RequestSession
import com.example.bookinghotel.data.remote.dto.*
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException

class TokenAuthenticatorTest {
    private class RefreshApi(val action: suspend () -> AuthResponseDto) : AuthApiService {
        override suspend fun refresh(request: RefreshTokenRequestDto) = action()
        override suspend fun register(request: RegisterRequestDto): AuthResponseDto = error("unused")
        override suspend fun login(request: LoginRequestDto): AuthResponseDto = error("unused")
        override suspend fun logout(request: RefreshTokenRequestDto) = Unit
    }
    private fun refreshed() = AuthResponseDto("new-token", "new-refresh", 3600,
        UserDto(1, "user1@test", "User 1", "USER"))
    private fun unauthorized(store: FakeSessionStore): Response {
        val session = store.session.value!!
        val request = Request.Builder().url("https://example.invalid/api/bookings")
            .header("Authorization", "Bearer ${session.accessToken}")
            .tag(RequestSession::class.java, RequestSession(session.sessionId)).build()
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").build()
    }
    @Test fun threeSimultaneous401s_makeExactlyOneRefreshAndReuseNewToken() {
        val store = FakeSessionStore()
        val calls = AtomicInteger()
        val api = RefreshApi { calls.incrementAndGet(); Thread.sleep(50); refreshed() }
        val authenticator = TokenAuthenticator(api, store, TokenRefreshCoordinator())
        val response = unauthorized(store)
        val pool = Executors.newFixedThreadPool(3)
        val ready = CountDownLatch(3); val start = CountDownLatch(1)
        try {
            val requests = (1..3).map { pool.submit<Request?> {
                ready.countDown(); check(start.await(5, TimeUnit.SECONDS))
                authenticator.authenticate(null, response)
            } }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown()
            requests.forEach { assertEquals("Bearer new-token", it.get(5, TimeUnit.SECONDS)?.header("Authorization")) }
            assertEquals(1, calls.get())
        } finally { pool.shutdownNow() }
    }
    @Test fun invalidRefresh401And403_clearSessionAndPrivateCache() = runBlocking {
        for (status in listOf(401,403)) {
            val store = FakeSessionStore(); var clears = 0; store.onClear = { clears++ }
            val response = unauthorized(store)
            val api = RefreshApi { throw HttpException(retrofit2.Response.error<AuthResponseDto>(status,
                okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{}"))) }
            assertNull(TokenAuthenticator(api, store, TokenRefreshCoordinator()).authenticate(null, response))
            assertNull(store.currentSession()); assertEquals(1, clears)
        }
    }
    @Test fun temporaryFailureKeepsSessionAndAllowsFutureRetry() = runBlocking {
        val store = FakeSessionStore(); val before = store.currentSession()
        val api = RefreshApi { throw IOException("offline") }
        assertNull(TokenAuthenticator(api,store,TokenRefreshCoordinator()).authenticate(null,unauthorized(store)))
        assertEquals(before,store.currentSession())
    }
    @Test fun logoutWhileRefreshInProgress_doesNotRestoreOldSession() = runBlocking {
        val store = FakeSessionStore(); val response = unauthorized(store)
        val api = RefreshApi { store.clear(); refreshed() }
        assertNull(TokenAuthenticator(api,store,TokenRefreshCoordinator()).authenticate(null,response))
        assertNull(store.currentSession())
    }
    @Test fun sessionChangeWhileRefreshInProgress_doesNotOverwriteNewUser() = runBlocking {
        val store = FakeSessionStore(); val response = unauthorized(store)
        val userB = testSession(2)
        val api = RefreshApi { store.save(userB); refreshed() }
        assertNull(TokenAuthenticator(api,store,TokenRefreshCoordinator()).authenticate(null,response))
        assertEquals(userB,store.currentSession())
    }
    @Test fun previousUser401IsNeverRetriedWithNewUserToken() = runBlocking {
        val store = FakeSessionStore(); val response = unauthorized(store); val calls = AtomicInteger()
        store.save(testSession(2))
        val api = RefreshApi { calls.incrementAndGet(); refreshed() }
        assertNull(TokenAuthenticator(api,store,TokenRefreshCoordinator()).authenticate(null,response))
        assertEquals(0,calls.get())
    }
    @Test fun invalidOldRefreshDoesNotClearNewSession() = runBlocking {
        val store = FakeSessionStore(); val response = unauthorized(store); val userB = testSession(2)
        val api = RefreshApi {
            store.save(userB)
            throw HttpException(retrofit2.Response.error<AuthResponseDto>(401,
                okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), "{}")))
        }
        assertNull(TokenAuthenticator(api,store,TokenRefreshCoordinator()).authenticate(null,response))
        assertEquals(userB,store.currentSession())
    }
}
