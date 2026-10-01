package com.example.bookinghotel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bookinghotel.data.auth.AuthSession
import com.example.bookinghotel.ui.AdminScreen
import com.example.bookinghotel.ui.AdminViewModel
import com.example.bookinghotel.ui.AuthViewModel
import com.example.bookinghotel.ui.BookingViewModel
import com.example.bookinghotel.ui.Screen
import com.example.bookinghotel.ui.screens.AuthScreen
import com.example.bookinghotel.ui.screens.BookingHistoryScreen
import com.example.bookinghotel.ui.screens.BookingSummaryScreen
import com.example.bookinghotel.ui.screens.PaymentScreen
import com.example.bookinghotel.ui.screens.ProfileScreen
import com.example.bookinghotel.ui.screens.RoomDetailsScreen
import com.example.bookinghotel.ui.screens.RoomListScreen
import com.example.bookinghotel.ui.screens.admin.AdminBookingsScreen
import com.example.bookinghotel.ui.screens.admin.AdminDashboardScreen
import com.example.bookinghotel.ui.screens.admin.AdminRoomsScreen

@Composable
fun BookingHotelApp(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val session by authViewModel.session.collectAsState()
    val currentSession = session
    if (currentSession == null) {
        AuthScreen(authViewModel)
        return
    }

    if (currentSession.user.role.equals("ADMIN", ignoreCase = true)) {
        AdminBookingHotelAuthenticatedContent(
            modifier = modifier,
            session = currentSession,
            onLogout = authViewModel::logout
        )
    } else {
        UserBookingHotelEntry(
            modifier = modifier,
            session = currentSession,
            onLogout = authViewModel::logout
        )
    }
}

@Composable
private fun UserBookingHotelEntry(
    modifier: Modifier,
    session: AuthSession,
    onLogout: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel()
) {
    LaunchedEffect(session.user.id) {
        viewModel.refreshBookingHistory()
    }

    BookingHotelAuthenticatedContent(
        modifier = modifier,
        viewModel = viewModel,
        session = session,
        onLogout = onLogout
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHotelAuthenticatedContent(
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel,
    session: AuthSession = demoSession(),
    onLogout: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val canNavigateBack = currentRoute != null && currentRoute != Screen.List.route

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(routeTitle(currentRoute), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                actions = {
                    if (currentRoute == Screen.List.route) {
                        IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Tài khoản")
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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Screen.List.route) { RoomListScreen(viewModel, navController) }
            composable(Screen.Detail.route) { RoomDetailsScreen(viewModel, navController) }
            composable(Screen.Summary.route) { BookingSummaryScreen(viewModel, navController) }
            composable(Screen.History.route) { BookingHistoryScreen(viewModel) }
            composable(Screen.Payment.route) { PaymentScreen(viewModel, navController) }
            composable(Screen.Profile.route) { ProfileScreen(session, onLogout) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminBookingHotelAuthenticatedContent(
    modifier: Modifier = Modifier,
    session: AuthSession,
    onLogout: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val canNavigateBack = currentRoute != null && currentRoute != AdminScreen.Dashboard.route

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(adminRouteTitle(currentRoute), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                actions = {
                    if (currentRoute == AdminScreen.Dashboard.route) {
                        IconButton(onClick = { navController.navigate(AdminScreen.Profile.route) }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Tài khoản admin")
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
            startDestination = AdminScreen.Dashboard.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(AdminScreen.Dashboard.route) {
                AdminDashboardScreen(
                    state = state,
                    onRefresh = viewModel::refreshDashboard,
                    onOpenRooms = { navController.navigate(AdminScreen.Rooms.route) },
                    onOpenBookings = { navController.navigate(AdminScreen.Bookings.route) }
                )
            }
            composable(AdminScreen.Rooms.route) {
                AdminRoomsScreen(
                    state = state,
                    onRefresh = viewModel::refreshRooms,
                    onUpdateRoom = viewModel::updateRoom,
                    onClearMessage = viewModel::clearMessage
                )
            }
            composable(AdminScreen.Bookings.route) {
                AdminBookingsScreen(
                    state = state,
                    onRefresh = viewModel::refreshBookings
                )
            }
            composable(AdminScreen.Profile.route) {
                ProfileScreen(session, onLogout)
            }
        }
    }
}

private fun routeTitle(route: String?): String = when (route) {
    Screen.Detail.route -> "Chi tiết phòng"
    Screen.Summary.route -> "Xác nhận đặt phòng"
    Screen.History.route -> "Lịch sử đặt phòng"
    Screen.Payment.route -> "Thanh toán"
    Screen.Profile.route -> "Tài khoản"
    else -> "Booking Hotel"
}

private fun adminRouteTitle(route: String?): String = when (route) {
    AdminScreen.Rooms.route -> "Quản lý phòng"
    AdminScreen.Bookings.route -> "Booking & Payment"
    AdminScreen.Profile.route -> "Tài khoản admin"
    else -> "Admin Dashboard"
}

private fun demoSession() = AuthSession(
    accessToken = "",
    refreshToken = "",
    accessTokenExpiresAt = Long.MAX_VALUE,
    user = com.example.bookinghotel.data.auth.UserProfile(0, "demo@local", "Demo User", "USER")
)
