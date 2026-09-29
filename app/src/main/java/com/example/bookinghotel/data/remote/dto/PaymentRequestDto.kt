package com.example.bookinghotel.data.remote.dto

data class PaymentRequestDto(
    val method: String,
    val simulateFailure: Boolean,
    val idempotencyKey: String
)
