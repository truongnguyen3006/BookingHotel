package com.example.bookinghotel.ui

import androidx.compose.ui.res.stringResource
import com.example.bookinghotel.R
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

@androidx.compose.runtime.Composable
fun String.toBookingStatusLabel(): String {
    return when (this) {
        "PENDING_PAYMENT" -> stringResource(R.string.cho_thanh_toan)
        "PROCESSING" -> stringResource(R.string.ang_xu_ly_thanh_toan)
        "SUCCESS" -> stringResource(R.string.a_thanh_toan)
        "FAILED" -> stringResource(R.string.that_bai)
        else -> this
    }
}

@androidx.compose.runtime.Composable
fun String.toRoomLabel(): String {
    return when (this) {
        "standard" -> stringResource(R.string.room_style_1)
        "deluxe" -> stringResource(R.string.room_style_2)
        "suite" -> stringResource(R.string.room_style_3)
        "executive" -> stringResource(R.string.room_style_4)
        "family" -> stringResource(R.string.room_style_5)
        else -> this
    }
}

fun Long.toVndLabel(): String {
    return NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(this)
}
