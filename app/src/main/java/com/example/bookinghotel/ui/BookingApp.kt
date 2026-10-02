package com.example.bookinghotel

import android.net.Uri
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
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.example.bookinghotel.R
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
import com.example.bookinghotel.ui.paymentReturnBookingId
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
    authViewModel: AuthViewModel = hiltViewModel(),
    paymentReturnUri: Uri? = null,
    onPaymentReturnConsumed: () -> Unit = {}
) {
    val session by authViewModel.session.collectAsState()
    val currentSession = session
    if (currentSession == null) {
        AuthScreen(authViewModel)
        return
    }

    key(currentSession.sessionId) {
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
                onLogout = authViewModel::logout,
                paymentReturnUri = paymentReturnUri,
                onPaymentReturnConsumed = onPaymentReturnConsumed
            )
        }
    }
}

@Composable
private fun UserBookingHotelEntry(
    modifier: Modifier,
    session: AuthSession,
    onLogout: () -> Unit,
    paymentReturnUri: Uri?,
    onPaymentReturnConsumed: () -> Unit,
    viewModel: BookingViewModel = hiltViewModel(key = "booking-${session.sessionId}")
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadRooms()
                viewModel.refreshBookingHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session.sessionId) {
        viewModel.refreshBookingHistory()
        while (isActive) {
            delay(30_000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                viewModel.refreshBookingHistory()
            }
        }
    }

    BookingHotelAuthenticatedContent(
        modifier = modifier,
        viewModel = viewModel,
        session = session,
        onLogout = onLogout,
        paymentReturnUri = paymentReturnUri,
        onPaymentReturnConsumed = onPaymentReturnConsumed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingHotelAuthenticatedContent(
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel,
    session: AuthSession = demoSession(),
    onLogout: () -> Unit = {},
    paymentReturnUri: Uri? = null,
    onPaymentReturnConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val canNavigateBack = currentRoute != null && currentRoute != Screen.List.route

    val snackbarState = remember { SnackbarHostState() }
    val returnUnavailable = stringResource(R.string.payment_return_unavailable)
    // Resolve only against freshly fetched, owned history. A URI is an untrusted hint.
    LaunchedEffect(paymentReturnUri, session.sessionId) {
        val uri = paymentReturnUri ?: return@LaunchedEffect
        val bookingId = paymentReturnBookingId(uri.toString())
        if (bookingId == null) {
            onPaymentReturnConsumed()
            snackbarState.showSnackbar(returnUnavailable)
            return@LaunchedEffect
        }
        viewModel.handlePaymentReturn(bookingId) { booking ->
            if (booking != null) {
                viewModel.resumePayment(booking)
                navController.navigate(Screen.Payment.route) { launchSingleTop = true }
                if (booking.status != "SUCCESS" && booking.status != "FAILED") viewModel.checkVnPayPaymentStatus()
            }
        }
        onPaymentReturnConsumed()
    }
    val historyError by viewModel.historyError.collectAsState()
    LaunchedEffect(historyError) { historyError?.let { snackbarState.showSnackbar(it) } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(routeTitle(currentRoute), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (currentRoute == Screen.List.route) {
                        IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = stringResource(R.string.tai_khoan))
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
            composable(Screen.History.route) { BookingHistoryScreen(viewModel, navController) }
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
    viewModel: AdminViewModel = hiltViewModel(key = "admin-${session.sessionId}")
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (currentRoute == AdminScreen.Dashboard.route) {
                        IconButton(onClick = { navController.navigate(AdminScreen.Profile.route) }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = stringResource(R.string.tai_khoan_admin))
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

@Composable
private fun routeTitle(route: String?): String = when (route) {
    Screen.Detail.route -> stringResource(R.string.chi_tiet_phong)
    Screen.Summary.route -> stringResource(R.string.xac_nhan_at_phong)
    Screen.History.route -> stringResource(R.string.lich_su_at_phong)
    Screen.Payment.route -> stringResource(R.string.pay_now)
    Screen.Profile.route -> stringResource(R.string.tai_khoan)
    else -> stringResource(R.string.app_name)
}

@Composable
private fun adminRouteTitle(route: String?): String = when (route) {
    AdminScreen.Rooms.route -> stringResource(R.string.quan_ly_phong)
    AdminScreen.Bookings.route -> stringResource(R.string.booking_payment)
    AdminScreen.Profile.route -> stringResource(R.string.tai_khoan_admin)
    else -> stringResource(R.string.admin_dashboard)
}

private fun demoSession() = AuthSession(
    accessToken = "",
    refreshToken = "",
    accessTokenExpiresAt = Long.MAX_VALUE,
    user = com.example.bookinghotel.data.auth.UserProfile(0, "demo@local", "Demo User", "USER")
)
