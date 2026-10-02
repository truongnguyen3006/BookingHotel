package com.example.bookinghotel

import com.example.bookinghotel.ui.paymentReturnBookingId
import org.junit.Assert.*
import org.junit.Test

class PaymentReturnTest {
    @Test fun rejectsMissingMalformedAndForeignSchemeIds() {
        listOf("bookinghotel://payment-result", "bookinghotel://payment-result?bookingId=abc",
            "bookinghotel://payment-result?bookingId=0", "bookinghotel://payment-result?bookingId=-1",
            "bookinghotel://payment-result?bookingId=2147483648",
            "bookinghotel://payment-result?bookingId=1&bookingId=2",
            "https://payment-result?bookingId=1").forEach { assertNull(paymentReturnBookingId(it)) }
    }
    @Test fun providerResultNeverChangesWhichBookingIsQueried() {
        assertEquals(42, paymentReturnBookingId("bookinghotel://payment-result?bookingId=42&responseCode=00"))
        assertEquals(42, paymentReturnBookingId("bookinghotel://payment-result?bookingId=42&responseCode=24"))
    }
}
