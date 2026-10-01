package com.example.bookinghotel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithTag("book_button").performClick()
        composeRule.onNodeWithTag("confirm_booking_button").performClick()

        composeRule.onNodeWithText("Đặt phòng thành công").assertIsDisplayed()
        composeRule.onNodeWithTag("summary_pay_button").performClick()

        composeRule.onNodeWithTag("payment_submit_button").performClick()
        composeRule.onNodeWithTag("confirm_payment_button").performClick()
        composeRule.onNodeWithText("Thanh toán thành công").assertIsDisplayed()
        composeRule.onNodeWithTag("payment_history_button").performClick()

        composeRule.onNodeWithText("Booking #1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Đã thanh toán")[0].assertIsDisplayed()
    }

    @Test
    fun negativeQuantity_showsValidationAndDisablesBookingButton() {
        launchApp()

        composeRule.onNodeWithTag("room_card_1").performClick()
        composeRule.onNodeWithTag("quantity_input").performTextClearance()
        composeRule.onNodeWithTag("quantity_input").performTextInput("-1")

        composeRule.onNodeWithText("Số lượng đặt phòng tối thiểu là 1.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("book_button").assertIsNotEnabled()
    }

    private fun launchApp() {
        val viewModel = BookingViewModel(InMemoryRoomRepository())
        composeRule.setContent {
            BookingHotelTheme {
                BookingHotelAuthenticatedContent(viewModel = viewModel)
            }
        }
    }
}
