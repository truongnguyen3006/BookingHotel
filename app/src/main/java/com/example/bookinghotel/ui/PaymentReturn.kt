package com.example.bookinghotel.ui

import java.net.URI
import java.net.URLDecoder

fun paymentReturnBookingId(value: String): Int? = runCatching {
    val uri = URI(value)
    if (uri.scheme != "bookinghotel" || uri.host != "payment-result") return null
    val ids = uri.rawQuery.orEmpty().split("&").mapNotNull { part ->
        val pair = part.split("=", limit = 2)
        if (pair.size == 2 && URLDecoder.decode(pair[0], "UTF-8") == "bookingId") {
            URLDecoder.decode(pair[1], "UTF-8")
        } else null
    }
    ids.singleOrNull()?.toIntOrNull()?.takeIf { it > 0 }
}.getOrNull()
