package com.example.bookinghotel.data.remote.dto

data class VnPayCreateRequestDto(
    val idempotencyKey: String
)

data class VnPayCreateResponseDto(
    val bookingId: Int,
    val status: String,
    val paymentUrl: String,
    val txnRef: String,
    val amountVnd: Long,
    val expiresAt: Long
)

data class VnPayStatusResponseDto(
    val bookingId: Int,
    val status: String,
    val method: String,
    val transactionId: String?,
    val txnRef: String,
    val amountVnd: Long,
    val responseCode: String?,
    val message: String,
    val paidAt: Long?
)
