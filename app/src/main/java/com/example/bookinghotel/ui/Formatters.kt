package com.example.bookinghotel.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toDateLabel(): String {
    if (this <= 0L) return "--"
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(this))
}

fun Long.toDateTimeLabel(): String {
    if (this <= 0L) return "--"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(this))
}

/** Formats all project money as native Vietnamese Dong. */
fun Long.toCurrencyLabel(): String = toVndLabel()

fun String.toBookingStatusLabel(): String {
    return when (this) {
        "PENDING_PAYMENT" -> "Chờ thanh toán"
        "PROCESSING" -> "Đang xử lý thanh toán"
        "SUCCESS" -> "Đã thanh toán"
        "FAILED" -> "Thất bại"
        else -> this
    }
}

fun String.toRoomLabel(): String {
    return when (this) {
        "standard" -> "Standard Room"
        "deluxe" -> "Deluxe Room"
        "suite" -> "Suite Room"
        "executive" -> "Executive Room"
        "family" -> "Family Room"
        else -> this
    }
}

fun Long.toVndLabel(): String {
    return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(this)
}
