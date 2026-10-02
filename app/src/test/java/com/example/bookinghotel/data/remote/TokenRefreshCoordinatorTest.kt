package com.example.bookinghotel.data.remote

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenRefreshCoordinatorTest {
    private data class Token(val value: String)

    @Test
    fun concurrent401s_refreshOnlyOnce_andSecondCallerReusesNewToken() = runTest {
        val coordinator = TokenRefreshCoordinator()
        val refreshCalls = AtomicInteger(0)
        var current = Token("old-token")

        suspend fun attempt(): Token? = coordinator.refreshSingleFlight(
            failedAccessToken = "old-token",
            currentValue = { current },
            accessTokenOf = Token::value
        ) {
            refreshCalls.incrementAndGet()
            delay(10)
            Token("new-token").also { current = it }
        }

        val first = async { attempt() }
        val second = async { attempt() }

        assertEquals("new-token", first.await()?.value)
        assertEquals("new-token", second.await()?.value)
        assertEquals(1, refreshCalls.get())
    }
}
