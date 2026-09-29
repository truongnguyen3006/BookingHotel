package com.example.bookinghotel.ui

sealed class Screen(val route: String) {
    object List : Screen("list")
    object Detail : Screen("detail")
    object Summary : Screen("summary")
    object History : Screen("history")
}
