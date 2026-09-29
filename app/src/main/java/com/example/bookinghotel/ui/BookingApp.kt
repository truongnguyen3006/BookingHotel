package com.example.bookinghotel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.screens.BookingHistoryScreen
import com.example.bookinghotel.ui.screens.BookingSummaryScreen
import com.example.bookinghotel.ui.screens.PaymentScreen
import com.example.bookinghotel.ui.screens.RoomDetailsScreen
import com.example.bookinghotel.ui.screens.RoomListScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHotelApp(
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val canNavigateBack = currentRoute != null && currentRoute != Screen.List.route

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = routeTitle(currentRoute),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.List.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(route = Screen.List.route) {
                RoomListScreen(viewModel, navController)
            }
            composable(Screen.Detail.route) {
                RoomDetailsScreen(viewModel, navController)
            }
            composable(Screen.Summary.route) {
                BookingSummaryScreen(viewModel, navController)
            }
            composable(Screen.History.route) {
                BookingHistoryScreen(viewModel)
            }
            composable(Screen.Payment.route) {
                PaymentScreen(viewModel, navController)
            }
        }
    }
}

private fun routeTitle(route: String?): String {
    return when (route) {
        Screen.Detail.route -> "Chi tiết phòng"
        Screen.Summary.route -> "Xác nhận đặt phòng"
        Screen.History.route -> "Lịch sử đặt phòng"
        Screen.Payment.route -> "Thanh toán"
        else -> "Booking Hotel"
    }
}
