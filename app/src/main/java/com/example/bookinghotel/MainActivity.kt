package com.example.bookinghotel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.bookinghotel.ui.theme.BookingHotelTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val paymentReturnUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val deepLink by paymentReturnUri.collectAsState()
            BookingHotelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                ) {
                    BookingHotelApp(
                        modifier = Modifier,
                        paymentReturnUri = deepLink,
                        onPaymentReturnConsumed = { paymentReturnUri.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == PAYMENT_SCHEME && data.host == PAYMENT_RESULT_HOST) {
            paymentReturnUri.value = data
        }
    }

    companion object {
        private const val PAYMENT_SCHEME = "bookinghotel"
        private const val PAYMENT_RESULT_HOST = "payment-result"
    }
}
