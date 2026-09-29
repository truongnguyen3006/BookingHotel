package com.example.bookinghotel.data.remote.dto

data class PaymentResponseDto(
    val bookingId: Int,
    val status: String,
    val method: String,
    val transactionId: String?,
    val message: String,
    val paidAt: Long? = null
)
