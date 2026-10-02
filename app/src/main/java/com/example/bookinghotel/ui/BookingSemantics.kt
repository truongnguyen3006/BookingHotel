package com.example.bookinghotel.ui

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

val BookingStatusKey = SemanticsPropertyKey<String>("BookingStatus")
var SemanticsPropertyReceiver.bookingStatus by BookingStatusKey
