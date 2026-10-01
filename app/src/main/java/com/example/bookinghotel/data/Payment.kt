package com.example.bookinghotel.data

enum class PaymentMethod {
    CARD,
    QR,
    VNPAY
}

data class PaymentResult(
    val bookingId: Int,
    val status: String,
    val method: PaymentMethod,
    val transactionId: String?,
    val message: String,
    val paidAt: Long? = null
)

data class VnPayPaymentSession(
    val bookingId: Int,
    val paymentUrl: String,
    val txnRef: String,
    val amountVnd: Long,
    val expiresAt: Long
)

data class VnPayPaymentStatus(
    val bookingId: Int,
    val status: String,
    val transactionId: String?,
    val txnRef: String,
    val amountVnd: Long,
    val responseCode: String?,
    val message: String,
    val paidAt: Long?
)
