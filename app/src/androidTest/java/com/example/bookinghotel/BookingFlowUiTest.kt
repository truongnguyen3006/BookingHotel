package com.example.bookinghotel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import com.example.bookinghotel.ui.BookingStatusKey
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.bookinghotel.data.repository.InMemoryRoomRepository
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.theme.BookingHotelTheme
import org.junit.Rule
import org.junit.Test

class BookingFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bookingAndPayment_successFlowAppearsInHistory() {
        launchApp()

        composeRule.onNodeWithTag("room_card_1").performClick()
        composeRule.onNodeWithTag("quantity_input").performTextClearance()
        composeRule.onNodeWithTag("quantity_input").performTextInput("2")
        composeRule.onNodeWithTag("book_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_booking_button").performClick()

        composeRule.onNodeWithTag("booking_success_message").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("summary_pay_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("payment_submit_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_payment_button").performClick()
        composeRule.onNodeWithTag("payment_success_message").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("payment_history_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("booking_history_item_1").assertIsDisplayed()
        composeRule.onNodeWithTag("booking_status_1").assert(SemanticsMatcher.expectValue(BookingStatusKey, "SUCCESS"))
    }

    @Test
    fun negativeQuantity_showsValidationAndDisablesBookingButton() {
        launchApp()

        composeRule.onNodeWithTag("room_card_1").performClick()
        composeRule.onNodeWithTag("quantity_input").performTextClearance()
        composeRule.onNodeWithTag("quantity_input").performTextInput("-1")

        composeRule.onNodeWithTag("booking_validation_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_button").assertIsNotEnabled()
    }

    @Test
    fun failedPayment_retrySucceedsAndUpdatesHistoryStatus() {
        launchApp()
        composeRule.onNodeWithTag("room_card_1").performClick()
        composeRule.onNodeWithTag("book_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_booking_button").performClick()
        composeRule.onNodeWithTag("summary_pay_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("simulate_failure_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("payment_submit_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_payment_button").performClick()
        composeRule.onNodeWithTag("payment_failed_message").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("simulate_failure_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("payment_retry_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_payment_button").performClick()
        composeRule.onNodeWithTag("payment_success_message").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("payment_history_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("booking_status_1").assert(SemanticsMatcher.expectValue(BookingStatusKey, "SUCCESS"))
    }

    @Test
    fun confirmationStaysVisibleAndDisabledDuringBookingRequest() {
        val delegate = InMemoryRoomRepository()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : com.example.bookinghotel.data.repository.RoomRepository by delegate {
            override suspend fun bookRoom(roomId: Int, quantity: Int, checkInDate: Long, checkOutDate: Long, guests: Int): Result<com.example.bookinghotel.data.Booking> {
                calls++
                release.await()
                return delegate.bookRoom(roomId, quantity, checkInDate, checkOutDate, guests)
            }
        }
        val vm = BookingViewModel(repository, com.example.bookinghotel.ui.AndroidAppStrings(androidx.test.core.app.ApplicationProvider.getApplicationContext()))
        composeRule.setContent { BookingHotelTheme { BookingHotelAuthenticatedContent(viewModel = vm) } }
        composeRule.onNodeWithTag("room_card_1").performClick()
        composeRule.onNodeWithTag("book_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_booking_button").performClick()
        composeRule.onNodeWithTag("confirm_booking_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("booking_confirmation_loading").assertIsDisplayed()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(1, calls); release.complete(Unit) }
        composeRule.onNodeWithTag("booking_success_message").performScrollTo().assertIsDisplayed()
    }

    private fun launchApp() {
        val viewModel = BookingViewModel(InMemoryRoomRepository(), com.example.bookinghotel.ui.AndroidAppStrings(androidx.test.core.app.ApplicationProvider.getApplicationContext()))
        composeRule.setContent {
            BookingHotelTheme {
                BookingHotelAuthenticatedContent(viewModel = viewModel)
            }
        }
    }
}
