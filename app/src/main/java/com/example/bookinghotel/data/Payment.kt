package com.example.bookinghotel.data

enum class PaymentMethod {
    CARD,
    QR
}

data class PaymentResult(
    val bookingId: Int,
    val status: String,
    val method: PaymentMethod,
    val transactionId: String?,
    val message: String
)
