package com.example.bookinghotel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color(0xFF424242),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFEFEFEF)
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
