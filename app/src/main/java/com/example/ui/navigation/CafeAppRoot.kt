package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.CafeRepository
import com.example.data.model.CafeOrder
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.data.model.PortalSyncStatus
import com.example.data.model.UiState
import com.example.ui.CafeViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.billing.BillingScreen
import com.example.ui.billing.PaymentCheckoutSheet
import com.example.ui.components.DigitalReceiptDialog
import com.example.ui.kitchen.KitchenScreen
import com.example.ui.portal.PortalSettingsScreen
import com.example.ui.qr.QrOrderLinkScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private data class NavDestinationItem(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val CafeDestinations = listOf(
    NavDestinationItem(
        route = "orders",
        labelResId = R.string.nav_orders,
        selectedIcon = Icons.Filled.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen
    ),
    NavDestinationItem(
        route = "qr_link",
        labelResId = R.string.nav_qr_link,
        selectedIcon = Icons.Filled.QrCode2,
        unselectedIcon = Icons.Outlined.QrCode2
    ),
    NavDestinationItem(
        route = "billing",
        labelResId = R.string.nav_billing,
        selectedIcon = Icons.Filled.Payments,
        unselectedIcon = Icons.Outlined.Payments
    ),
    NavDestinationItem(
        route = "portal",
        labelResId = R.string.nav_portal,
        selectedIcon = Icons.Filled.Storefront,
        unselectedIcon = Icons.Outlined.Storefront
    )
)

@Composable
fun CafeAppRoot(
    deepLinkTableCode: String? = null,
    onConsumeDeepLink: () -> Unit = {},
    openOrdersNonce: Int = 0,
    auth: FirebaseAuth = Firebase.auth
) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedCafeWorkspace(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty(),
                deepLinkTableCode = deepLinkTableCode,
                onConsumeDeepLink = onConsumeDeepLink,
                openOrdersNonce = openOrdersNonce
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedCafeWorkspace(
    currentUserId: String,
    userEmail: String,
    deepLinkTableCode: String?,
    onConsumeDeepLink: () -> Unit,
    openOrdersNonce: Int
) {
    val viewModel: CafeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dbId = app.getString(R.string.firestore_database_id)
                val defaultPortal = app.getString(R.string.default_portal_url)
                val db = FirebaseFirestore.getInstance(dbId)
                CafeViewModel(
                    repository = CafeRepository(db),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext,
                    defaultPortalUrl = defaultPortal
                )
            }
        }
    )

    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val portalMenuState by viewModel.portalMenuState.collectAsStateWithLifecycle()
    val portalTableCount by viewModel.portalTableCount.collectAsStateWithLifecycle()
    val qrTablesState by viewModel.qrTablesState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val ampleCart by viewModel.ampleCart.collectAsStateWithLifecycle()
    val latestAlertOrder by viewModel.latestIncomingAlertOrder.collectAsStateWithLifecycle()
    val staffKey by viewModel.staffKey.collectAsStateWithLifecycle()
    val backgroundMonitorEnabled by viewModel.backgroundMonitorEnabled.collectAsStateWithLifecycle()
    val portalSyncStatus by viewModel.portalSyncStatus.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var checkoutOrder by remember { mutableStateOf<CafeOrder?>(null) }
    var receiptOrder by remember { mutableStateOf<CafeOrder?>(null) }
    var overridePortalUrl by remember { mutableStateOf<String?>(null) }

    val allOrders = (ordersState as? UiState.Success)?.data.orEmpty()
    val newOrdersCount = allOrders.count { it.parsedStatus == OrderStatus.NEW }
    val unpaidOrdersCount = allOrders.count {
        it.parsedPaymentStatus == PaymentStatus.UNPAID && it.parsedStatus != OrderStatus.CANCELLED
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "orders"

    val navigateToRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(deepLinkTableCode) {
        if (!deepLinkTableCode.isNullOrBlank()) {
            navigateToRoute("qr_link")
        }
    }

    LaunchedEffect(openOrdersNonce) {
        if (openOrdersNonce > 0) {
            navigateToRoute("orders")
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = settings.cafeName,
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (portalSyncStatus is PortalSyncStatus.Connected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (portalSyncStatus is PortalSyncStatus.Connected) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                }
                                            )
                                    )
                                    Text(
                                        text = if (portalSyncStatus is PortalSyncStatus.Connected) "SYNCED" else "OFFLINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (currentRoute != "qr_link") {
                            TextButton(
                                onClick = { navigateToRoute("qr_link") },
                                modifier = Modifier.testTag("top_qr_hub_action")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("QR Hub")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        CafeDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                "orders" -> newOrdersCount
                                "billing" -> unpaidOrdersCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = {
                                    NavIconWithBadge(
                                        icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        label = stringResource(dest.labelResId),
                                        badgeCount = badgeCount
                                    )
                                },
                                label = { Text(stringResource(dest.labelResId)) },
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        CafeDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                "orders" -> newOrdersCount
                                "billing" -> unpaidOrdersCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(dest.route) },
                                icon = {
                                    NavIconWithBadge(
                                        icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        label = stringResource(dest.labelResId),
                                        badgeCount = badgeCount
                                    )
                                },
                                label = { Text(stringResource(dest.labelResId)) },
                                modifier = Modifier.testTag("rail_tab_${dest.route}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "orders",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("orders") {
                            KitchenScreen(
                                ordersState = ordersState,
                                settings = settings,
                                latestAlertOrder = latestAlertOrder,
                                portalSyncStatus = portalSyncStatus,
                                backgroundMonitorEnabled = backgroundMonitorEnabled,
                                isSubmitting = isSubmitting,
                                onEnableBackgroundMonitor = { viewModel.setBackgroundMonitorEnabled(true) },
                                onConnectStaffKey = viewModel::connectStaffKey,
                                onSyncPortalNow = viewModel::syncPortalOrdersNow,
                                onDismissAlert = viewModel::dismissIncomingAlert,
                                onAdvanceStatus = viewModel::advanceOrderStatus,
                                onCancelOrder = viewModel::cancelAndRemoveUnpaidOrder,
                                onCollectPayment = { order -> checkoutOrder = order },
                                onNavigateToQrLinks = { navigateToRoute("qr_link") }
                            )
                        }

                        composable("qr_link") {
                            QrOrderLinkScreen(
                                qrTablesState = qrTablesState,
                                portalMenuState = portalMenuState,
                                ampleCart = ampleCart,
                                portalTableCount = portalTableCount,
                                settings = settings,
                                isSubmitting = isSubmitting,
                                deepLinkTableCode = deepLinkTableCode,
                                onConsumeDeepLink = onConsumeDeepLink,
                                onAddQrTable = viewModel::addQrTable,
                                onToggleQrTable = viewModel::toggleQrTable,
                                onDeleteQrTable = viewModel::deleteQrTable,
                                onSyncTablesAndMenu = { viewModel.refreshPortalMenuAndTables(forceTableSync = true) },
                                onAddAmpleItemToCart = viewModel::addAmpleItemToCart,
                                onDecrementAmpleItemFromCart = viewModel::decrementAmpleItemFromCart,
                                onClearAmpleCart = viewModel::clearAmpleCart,
                                onOpenUrlInPortal = { url ->
                                    overridePortalUrl = url
                                    navigateToRoute("portal")
                                },
                                onPlaceAmpleQrOrder = { tableCode, name, phone, notes, adultOk, payMethod ->
                                    viewModel.submitAmpleCustomerQrOrder(
                                        tableCode = tableCode,
                                        customerName = name,
                                        customerPhone = phone,
                                        notes = notes,
                                        adultConfirmed = adultOk,
                                        immediatePaymentMethod = payMethod,
                                        onSuccess = { navigateToRoute("orders") }
                                    )
                                }
                            )
                        }

                        composable("billing") {
                            BillingScreen(
                                ordersState = ordersState,
                                settings = settings,
                                onCollectPayment = { order -> checkoutOrder = order },
                                onViewReceipt = { order -> receiptOrder = order }
                            )
                        }

                        composable("portal") {
                            PortalSettingsScreen(
                                settings = settings,
                                portalMenuState = portalMenuState,
                                staffKey = staffKey,
                                backgroundMonitorEnabled = backgroundMonitorEnabled,
                                portalSyncStatus = portalSyncStatus,
                                overridePortalUrl = overridePortalUrl,
                                onConsumeOverrideUrl = { overridePortalUrl = null },
                                userEmail = userEmail,
                                isSubmitting = isSubmitting,
                                onStaffKeyCaptured = viewModel::adoptWebViewStaffKey,
                                onConnectStaffKey = viewModel::connectStaffKey,
                                onDisconnectStaffKey = viewModel::disconnectStaffKey,
                                onToggleBackgroundMonitor = viewModel::setBackgroundMonitorEnabled,
                                onRefreshPortalMenu = { viewModel.refreshPortalMenuAndTables(forceTableSync = true) },
                                onSaveSettings = viewModel::saveSettings,
                                onSendTestAlert = viewModel::sendTestOrderNotification,
                                onSendDelayedTestAlert = viewModel::sendDelayedClosedAppTestNotification
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingPayOrder = checkoutOrder
    if (pendingPayOrder != null) {
        PaymentCheckoutSheet(
            order = pendingPayOrder,
            settings = settings,
            isSubmitting = isSubmitting,
            onDismiss = { checkoutOrder = null },
            onConfirmPayment = { method, tipCents, discountCents, ref, markCompleted ->
                viewModel.processPayment(
                    order = pendingPayOrder,
                    method = method,
                    tipCents = tipCents,
                    discountCents = discountCents,
                    paymentReference = ref,
                    markCompleted = markCompleted,
                    onSuccess = {
                        val updatedReceipt = pendingPayOrder.copy(
                            paymentStatus = PaymentStatus.PAID.name,
                            paymentMethod = method.name,
                            tipCents = tipCents,
                            discountCents = discountCents,
                            totalCents = ((pendingPayOrder.subtotalCents - discountCents).coerceAtLeast(0L) +
                                pendingPayOrder.taxCents + tipCents),
                            paymentReference = ref
                        )
                        checkoutOrder = null
                        receiptOrder = updatedReceipt
                    }
                )
            }
        )
    }

    val activeReceipt = receiptOrder
    if (activeReceipt != null) {
        DigitalReceiptDialog(
            order = activeReceipt,
            cafeName = settings.cafeName,
            currencySymbol = settings.currencySymbol,
            onDismiss = { receiptOrder = null }
        )
    }
}

@Composable
private fun NavIconWithBadge(
    icon: ImageVector,
    label: String,
    badgeCount: Int
) {
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (badgeCount > 99) "99+" else "$badgeCount")
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = label)
    }
}
