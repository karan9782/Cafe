package com.example.ui.kitchen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alerts.OrderAlertNotifier
import com.example.alerts.OrderMonitorController
import com.example.data.model.CafeOrder
import com.example.data.model.CafeSettings
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.PaymentStatus
import com.example.data.model.PortalSyncStatus
import com.example.data.model.UiState
import com.example.ui.components.IncomingOrderAlertBanner
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.formatElapsedDuration
import com.example.util.formatMoney
import kotlinx.coroutines.delay

private enum class OwnerOrderFilter(val label: String) {
    ACTIVE("Active Queue"),
    NEW("Incoming"),
    PREPARING("Preparing"),
    READY("Ready"),
    COMPLETED("Done")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitchenScreen(
    ordersState: UiState<List<CafeOrder>>,
    settings: CafeSettings,
    latestAlertOrder: CafeOrder?,
    portalSyncStatus: PortalSyncStatus,
    backgroundMonitorEnabled: Boolean,
    isSubmitting: Boolean,
    onEnableBackgroundMonitor: () -> Unit,
    onConnectStaffKey: (String) -> Unit,
    onSyncPortalNow: () -> Unit,
    onDismissAlert: () -> Unit,
    onAdvanceStatus: (CafeOrder, OrderStatus) -> Unit,
    onCancelOrder: (CafeOrder) -> Unit,
    onCollectPayment: (CafeOrder) -> Unit,
    onNavigateToQrLinks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(OrderAlertNotifier.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            OrderMonitorController.syncMonitoringState(context)
        }
    }

    var selectedFilter by remember { mutableStateOf(OwnerOrderFilter.ACTIVE) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Owner Alert Status / Permission Banner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Instant QR Order Notifications",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Get notified the moment a customer scans a table QR code and places an order.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.testTag("grant_notifications_button")
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }
        } else {
            item {
                val activeBg = settings.alertsEnabled && backgroundMonitorEnabled
                Surface(
                    color = if (activeBg) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (activeBg) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (activeBg) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                !settings.alertsEnabled -> "Order Notifications Paused in Settings"
                                !backgroundMonitorEnabled -> "Closed-App Alerts Paused"
                                else -> "Background Alerts Active • Works when app is closed"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activeBg) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (settings.alertsEnabled && !backgroundMonitorEnabled) {
                            TextButton(
                                onClick = onEnableBackgroundMonitor,
                                modifier = Modifier.testTag("enable_background_alerts_button")
                            ) {
                                Text("Turn On")
                            }
                        }
                    }
                }
            }
        }

        // Portal Live Sync Banner / Staff Key Connector
        item {
            PortalLiveSyncBanner(
                status = portalSyncStatus,
                isSubmitting = isSubmitting,
                onConnectStaffKey = onConnectStaffKey,
                onSyncPortalNow = onSyncPortalNow
            )
        }

        // Live Incoming Order Alert Banner
        item {
            IncomingOrderAlertBanner(
                alertOrder = latestAlertOrder,
                currencySymbol = settings.currencySymbol,
                onStartPrep = { order -> onAdvanceStatus(order, OrderStatus.PREPARING) },
                onDismiss = onDismissAlert
            )
        }

        when (ordersState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = ordersState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val allOrders = ordersState.data
                val newCount = allOrders.count { it.parsedStatus == OrderStatus.NEW }
                val prepCount = allOrders.count { it.parsedStatus == OrderStatus.PREPARING }
                val readyCount = allOrders.count { it.parsedStatus == OrderStatus.READY }
                val paidRevenueCents = allOrders
                    .filter { it.parsedPaymentStatus == PaymentStatus.PAID && it.parsedStatus != OrderStatus.CANCELLED }
                    .sumOf { it.totalCents }

                // Owner Command KPI Strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiMetricCard(
                            title = "Incoming",
                            value = "$newCount",
                            accentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Prep",
                            value = "$prepCount",
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Ready",
                            value = "$readyCount",
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Sales",
                            value = formatMoney(paidRevenueCents, settings.currencySymbol),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }

                // Filter Chips
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OwnerOrderFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("kitchen_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                val filteredOrders = when (selectedFilter) {
                    OwnerOrderFilter.ACTIVE -> allOrders.filter {
                        it.parsedStatus in setOf(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.READY)
                    }
                    OwnerOrderFilter.NEW -> allOrders.filter { it.parsedStatus == OrderStatus.NEW }
                    OwnerOrderFilter.PREPARING -> allOrders.filter { it.parsedStatus == OrderStatus.PREPARING }
                    OwnerOrderFilter.READY -> allOrders.filter { it.parsedStatus == OrderStatus.READY }
                    OwnerOrderFilter.COMPLETED -> allOrders.filter {
                        it.parsedStatus == OrderStatus.COMPLETED || it.parsedStatus == OrderStatus.CANCELLED
                    }
                }

                if (filteredOrders.isEmpty()) {
                    item {
                        EmptyOwnerOrdersCard(
                            selectedFilter = selectedFilter,
                            onNavigateToQrLinks = onNavigateToQrLinks
                        )
                    }
                } else {
                    items(filteredOrders, key = { it.id }) { order ->
                        OwnerOrderTicketCard(
                            order = order,
                            currencySymbol = settings.currencySymbol,
                            nowMillis = nowMillis,
                            onAdvanceStatus = onAdvanceStatus,
                            onCancelOrder = onCancelOrder,
                            onCollectPayment = onCollectPayment
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalLiveSyncBanner(
    status: PortalSyncStatus,
    isSubmitting: Boolean,
    onConnectStaffKey: (String) -> Unit,
    onSyncPortalNow: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    when (status) {
        is PortalSyncStatus.Connected -> {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Portal Synced • ${status.portalOrdersCount} order(s) today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSyncPortalNow) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync portal orders now",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        is PortalSyncStatus.Connecting -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Connecting to ample.run kitchen feed...",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        is PortalSyncStatus.Disconnected, is PortalSyncStatus.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (status is PortalSyncStatus.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Link Live Orders from ample.run Portal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Enter your portal Staff Key (or sign in on the Portal tab) so table QR orders from ample.run appear here & in Billing automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (status is PortalSyncStatus.Error) {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("Staff Key") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle key visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("portal_staff_key_input")
                        )
                        Button(
                            onClick = { onConnectStaffKey(keyInput) },
                            enabled = !isSubmitting && keyInput.isNotBlank(),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("connect_portal_key_button")
                        ) {
                            Text("Sync")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerOrderTicketCard(
    order: CafeOrder,
    currencySymbol: String,
    nowMillis: Long,
    onAdvanceStatus: (CafeOrder, OrderStatus) -> Unit,
    onCancelOrder: (CafeOrder) -> Unit,
    onCollectPayment: (CafeOrder) -> Unit
) {
    val status = order.parsedStatus
    val stripeColor = when (status) {
        OrderStatus.NEW -> MaterialTheme.colorScheme.error
        OrderStatus.PREPARING -> MaterialTheme.colorScheme.tertiary
        OrderStatus.READY -> MaterialTheme.colorScheme.secondary
        OrderStatus.COMPLETED, OrderStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (status == OrderStatus.NEW) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_ticket_${order.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(stripeColor)
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = order.orderNumber,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OrderStatusBadge(status = status)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (order.parsedOrderType == OrderType.QR_SCAN) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "QR scan order",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${order.tableOrCustomer} • ${order.parsedOrderType.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatElapsedDuration(order.createdAt, nowMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PaymentStatusBadge(order = order)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    order.items.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${line.quantity}x  ${line.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (line.notes.isNotBlank()) {
                                    Text(
                                        text = "Note: ${line.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = formatMoney(line.unitPriceCents * line.quantity, currencySymbol),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (order.notes.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: ${order.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bill ${formatMoney(order.totalCents, currencySymbol)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (status in setOf(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.READY) &&
                        order.parsedPaymentStatus == PaymentStatus.UNPAID &&
                        !order.id.startsWith("ample_")
                    ) {
                        TextButton(onClick = { onCancelOrder(order) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (status != OrderStatus.COMPLETED && status != OrderStatus.CANCELLED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (order.parsedPaymentStatus == PaymentStatus.UNPAID) {
                            OutlinedButton(
                                onClick = { onCollectPayment(order) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("ticket_pay_button_${order.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Collect ₹")
                            }
                        }

                        val (nextStatus, nextLabel, buttonColor) = when (status) {
                            OrderStatus.NEW -> Triple(
                                OrderStatus.PREPARING,
                                "Accept & Prep",
                                MaterialTheme.colorScheme.primary
                            )
                            OrderStatus.PREPARING -> Triple(
                                OrderStatus.READY,
                                "Mark Ready",
                                MaterialTheme.colorScheme.secondary
                            )
                            OrderStatus.READY -> Triple(
                                OrderStatus.COMPLETED,
                                "Complete",
                                MaterialTheme.colorScheme.secondary
                            )
                            OrderStatus.COMPLETED, OrderStatus.CANCELLED -> Triple(
                                OrderStatus.COMPLETED,
                                "Complete",
                                MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { onAdvanceStatus(order, nextStatus) },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("ticket_advance_button_${order.id}")
                        ) {
                            Icon(
                                imageVector = if (status == OrderStatus.NEW) Icons.Default.LocalFireDepartment else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(nextLabel)
                        }
                    }
                } else if (order.parsedPaymentStatus == PaymentStatus.UNPAID && status != OrderStatus.CANCELLED) {
                    Button(
                        onClick = { onCollectPayment(order) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Collect Payment (${formatMoney(order.totalCents, currencySymbol)})")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyOwnerOrdersCard(
    selectedFilter: OwnerOrderFilter,
    onNavigateToQrLinks: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_kitchen),
                contentDescription = "No active QR orders",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                text = if (selectedFilter == OwnerOrderFilter.ACTIVE) {
                    "Ready for QR Table Orders"
                } else {
                    "No ${selectedFilter.label} Orders"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Link your portal Staff Key above to pull live table orders from ample.run, or open QR & Link to place a table order directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onNavigateToQrLinks,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_orders_qr_cta")
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open QR & Barcode Ordering Hub")
            }
        }
    }
}
