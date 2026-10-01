package com.example.bookinghotel.ui

sealed class AdminScreen(val route: String) {
    object Dashboard : AdminScreen("admin_dashboard")
    object Rooms : AdminScreen("admin_rooms")
    object Bookings : AdminScreen("admin_bookings")
    object Profile : AdminScreen("admin_profile")
}
