package com.example.ui.portal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import com.example.alerts.OrderAlertNotifier
import com.example.alerts.OrderMonitorController
import com.example.data.model.CafeSettings
import com.example.data.model.PortalSyncStatus
import com.example.data.model.UiState
import com.example.data.remote.AmpleMenuCategoryDto
import com.example.ui.auth.signOut
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.formatMoney
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private enum class PortalMode(val label: String) {
    WEB_PORTAL("Web Portal"),
    MENU("Menu (₹)"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortalSettingsScreen(
    settings: CafeSettings,
    portalMenuState: UiState<List<AmpleMenuCategoryDto>>,
    staffKey: String,
    backgroundMonitorEnabled: Boolean,
    portalSyncStatus: PortalSyncStatus,
    overridePortalUrl: String?,
    onConsumeOverrideUrl: () -> Unit,
    userEmail: String,
    isSubmitting: Boolean,
    onStaffKeyCaptured: (String) -> Unit,
    onConnectStaffKey: (String) -> Unit,
    onDisconnectStaffKey: () -> Unit,
    onToggleBackgroundMonitor: (Boolean) -> Unit,
    onRefreshPortalMenu: () -> Unit,
    onSaveSettings: (
        cafeName: String,
        websiteUrl: String,
        upiId: String,
        currencySymbol: String,
        taxRatePercent: Double,
        alertsEnabled: Boolean,
        soundAlertsEnabled: Boolean
    ) -> Unit,
    onSendTestAlert: () -> Unit,
    onSendDelayedTestAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(PortalMode.WEB_PORTAL) }
    var activeWebUrl by remember(settings.websiteUrl) { mutableStateOf(settings.websiteUrl) }

    LaunchedEffect(overridePortalUrl) {
        val target = overridePortalUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        activeWebUrl = target
        mode = PortalMode.WEB_PORTAL
        onConsumeOverrideUrl()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Segmented Switcher
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = mode == PortalMode.WEB_PORTAL,
                onClick = { mode = PortalMode.WEB_PORTAL },
                leadingIcon = { Icon(Icons.Default.Web, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(PortalMode.WEB_PORTAL.label) },
                modifier = Modifier.testTag("portal_mode_web")
            )
            FilterChip(
                selected = mode == PortalMode.MENU,
                onClick = { mode = PortalMode.MENU },
                leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(PortalMode.MENU.label) },
                modifier = Modifier.testTag("portal_mode_menu")
            )
            FilterChip(
                selected = mode == PortalMode.SETTINGS,
                onClick = { mode = PortalMode.SETTINGS },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(PortalMode.SETTINGS.label) },
                modifier = Modifier.testTag("portal_mode_settings")
            )
        }

        when (mode) {
            PortalMode.WEB_PORTAL -> {
                WebKitchenPortalPane(
                    portalUrl = activeWebUrl,
                    onStaffKeyCaptured = onStaffKeyCaptured,
                    modifier = Modifier.weight(1f)
                )
            }
            PortalMode.MENU -> {
                PortalMenuViewerPane(
                    portalMenuState = portalMenuState,
                    currencySymbol = settings.currencySymbol,
                    isSubmitting = isSubmitting,
                    onRefreshMenu = onRefreshPortalMenu,
                    modifier = Modifier.weight(1f)
                )
            }
            PortalMode.SETTINGS -> {
                CafeSettingsPane(
                    settings = settings,
                    staffKey = staffKey,
                    backgroundMonitorEnabled = backgroundMonitorEnabled,
                    portalSyncStatus = portalSyncStatus,
                    userEmail = userEmail,
                    isSubmitting = isSubmitting,
                    onConnectStaffKey = onConnectStaffKey,
                    onDisconnectStaffKey = onDisconnectStaffKey,
                    onToggleBackgroundMonitor = onToggleBackgroundMonitor,
                    onSaveSettings = onSaveSettings,
                    onSendTestAlert = onSendTestAlert,
                    onSendDelayedTestAlert = onSendDelayedTestAlert,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebKitchenPortalPane(
    portalUrl: String,
    onStaffKeyCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    val safeUrl = remember(portalUrl) {
        val trimmed = portalUrl.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            trimmed
        } else {
            "https://gdp4fzx3f5u6.ample.run/kitchen#kitchen"
        }
    }
    val expectedHost = remember(safeUrl) { Uri.parse(safeUrl).host }

    // Periodically read mawa_staff_key from WebView localStorage after owner signs in on web
    LaunchedEffect(webViewRef, expectedHost) {
        while (true) {
            delay(2500L)
            val view = webViewRef ?: continue
            val currentHost = Uri.parse(view.url.orEmpty()).host
            if (expectedHost != null && currentHost == expectedHost) {
                view.evaluateJavascript(
                    "(function(){try{return window.localStorage.getItem('mawa_staff_key')||'';}catch(e){return '';}})();"
                ) { raw ->
                    val cleaned = raw?.trim()?.trim('"')?.replace("\\\"", "\"")?.trim().orEmpty()
                    if (cleaned.isNotEmpty() && cleaned != "null") {
                        onStaffKeyCaptured(cleaned)
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back in portal")
                }
                Text(
                    text = safeUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload portal")
                }
                IconButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open portal in browser")
                }
            }
        }

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url ?: return false
                                val scheme = target.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, target))
                                } catch (_: Exception) {
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val currentHost = Uri.parse(url.orEmpty()).host
                                if (expectedHost != null && currentHost == expectedHost) {
                                    view?.evaluateJavascript(
                                        "(function(){try{return window.localStorage.getItem('mawa_staff_key')||'';}catch(e){return '';}})();"
                                    ) { raw ->
                                        val cleaned = raw?.trim()?.trim('"')?.trim().orEmpty()
                                        if (cleaned.isNotEmpty() && cleaned != "null") {
                                            onStaffKeyCaptured(cleaned)
                                        }
                                    }
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(safeUrl)
                        webViewRef = this
                    }
                },
                update = { view ->
                    if (view.url != safeUrl && view.originalUrl != safeUrl) {
                        view.loadUrl(safeUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortalMenuViewerPane(
    portalMenuState: UiState<List<AmpleMenuCategoryDto>>,
    currencySymbol: String,
    isSubmitting: Boolean,
    onRefreshMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ample.run menu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onRefreshMenu,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("refresh_ample_menu_button")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync menu")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync")
                }
            }
        }

        when (portalMenuState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
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
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = portalMenuState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = onRefreshMenu) {
                                Text("Retry Menu Sync")
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                val categories = portalMenuState.data
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryName == null,
                            onClick = { selectedCategoryName = null },
                            label = { Text("All (${categories.sumOf { it.items.size }})") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryName == cat.name,
                                onClick = { selectedCategoryName = cat.name },
                                label = { Text("${cat.name} (${cat.items.size})") }
                            )
                        }
                    }
                }

                val query = searchQuery.trim()
                val visibleCategories = categories.filter {
                    selectedCategoryName == null || it.name == selectedCategoryName
                }
                visibleCategories.forEach { category ->
                    val filteredItems = category.items.filter { item ->
                        query.isEmpty() ||
                            item.name.contains(query, ignoreCase = true) ||
                            category.name.contains(query, ignoreCase = true)
                    }
                    if (filteredItems.isNotEmpty()) {
                        item(key = "header_${category.name}") {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(filteredItems, key = { it.id }) { item ->
                            val paise = (item.priceRupees * 100.0).roundToLong()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${category.name} • ID: ${item.id}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatMoney(paise, currencySymbol),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CafeSettingsPane(
    settings: CafeSettings,
    staffKey: String,
    backgroundMonitorEnabled: Boolean,
    portalSyncStatus: PortalSyncStatus,
    userEmail: String,
    isSubmitting: Boolean,
    onConnectStaffKey: (String) -> Unit,
    onDisconnectStaffKey: () -> Unit,
    onToggleBackgroundMonitor: (Boolean) -> Unit,
    onSaveSettings: (String, String, String, String, Double, Boolean, Boolean) -> Unit,
    onSendTestAlert: () -> Unit,
    onSendDelayedTestAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var cafeName by remember(settings) { mutableStateOf(settings.cafeName) }
    var websiteUrl by remember(settings) { mutableStateOf(settings.websiteUrl) }
    var upiId by remember(settings) { mutableStateOf(settings.upiId) }
    var currency by remember(settings) { mutableStateOf(settings.currencySymbol) }
    var taxText by remember(settings) { mutableStateOf(settings.taxRatePercent.toString()) }
    var alertsEnabled by remember(settings) { mutableStateOf(settings.alertsEnabled) }
    var soundAlertsEnabled by remember(settings) { mutableStateOf(settings.soundAlertsEnabled) }
    var staffKeyInput by remember(staffKey) { mutableStateOf(staffKey) }
    var showStaffKey by remember { mutableStateOf(false) }
    var hasNotifPerm by remember { mutableStateOf(OrderAlertNotifier.hasNotificationPermission(context)) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (granted) {
            OrderMonitorController.syncMonitoringState(context)
        }
    }

    LaunchedEffect(settings) {
        cafeName = settings.cafeName
        websiteUrl = settings.websiteUrl
        upiId = settings.upiId
        currency = settings.currencySymbol
        taxText = settings.taxRatePercent.toString()
        alertsEnabled = settings.alertsEnabled
        soundAlertsEnabled = settings.soundAlertsEnabled
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ample.run Live Sync Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = if (portalSyncStatus is PortalSyncStatus.Connected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = "Ample.run Portal Live Sync",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = when (portalSyncStatus) {
                        is PortalSyncStatus.Connected ->
                            "Connected • Syncing live orders (${portalSyncStatus.portalOrdersCount} today) to Orders & Billing."
                        is PortalSyncStatus.Connecting -> "Verifying staff key..."
                        is PortalSyncStatus.Error -> portalSyncStatus.message
                        PortalSyncStatus.Disconnected ->
                            "Enter your Ample staff key to pull live orders into Orders & Billing."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (portalSyncStatus is PortalSyncStatus.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                OutlinedTextField(
                    value = staffKeyInput,
                    onValueChange = { staffKeyInput = it },
                    label = { Text("Portal Staff Key") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showStaffKey = !showStaffKey }) {
                            Icon(
                                imageVector = if (showStaffKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    visualTransformation = if (showStaffKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConnectStaffKey(staffKeyInput) },
                        enabled = !isSubmitting && staffKeyInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Connect Key")
                    }
                    if (staffKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = onDisconnectStaffKey,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }

        // Real-Time Order Alerts Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Real-Time QR Order Alerts",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Push Notifications", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Alert owner immediately when a QR order arrives",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kitchen Chime Sound", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Play audio chime on new QR scan order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = soundAlertsEnabled, onCheckedChange = { soundAlertsEnabled = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Listening When App Is Closed", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Runs live background listener & periodic sync for orders even after closing app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = backgroundMonitorEnabled,
                        onCheckedChange = onToggleBackgroundMonitor,
                        modifier = Modifier.testTag("toggle_background_monitor")
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                    OutlinedButton(
                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Android Notification Permission")
                    }
                }

                OutlinedButton(
                    onClick = onSendTestAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_order_alert_button")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Test QR Order Notification")
                }

                OutlinedButton(
                    onClick = onSendDelayedTestAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_delayed_order_alert_button")
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Alert in 5s (Close App Now)")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_battery_settings_button")
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Battery & Background Settings")
                }
                Text(
                    text = "Tip: If your phone stops alerts after swiping apps away, open App Battery Settings and select Unrestricted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Storefront & Billing Config Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cafe Profile & Rupee Billing Setup",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = cafeName,
                    onValueChange = { cafeName = it.take(60) },
                    label = { Text("Cafe Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = websiteUrl,
                    onValueChange = { websiteUrl = it.take(300) },
                    label = { Text("Ample.run Portal URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it.take(100) },
                    label = { Text("Merchant UPI VPA (for QR Payments)") },
                    placeholder = { Text("yourcafe@okaxis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Currency Symbol", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹", "$", "€", "£").forEach { sym ->
                        FilterChip(
                            selected = currency == sym,
                            onClick = { currency = sym },
                            label = { Text(sym) }
                        )
                    }
                }
                OutlinedTextField(
                    value = taxText,
                    onValueChange = { taxText = it },
                    label = { Text("Additional Tax Rate (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val taxVal = (taxText.toDoubleOrNull() ?: settings.taxRatePercent).coerceIn(0.0, 30.0)
                        onSaveSettings(
                            cafeName.ifBlank { "Mawa’s Cafe" },
                            websiteUrl,
                            upiId,
                            currency,
                            taxVal,
                            alertsEnabled,
                            soundAlertsEnabled
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_settings_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Refresh Portal Data")
                }
            }
        }

        // Account & Sign Out Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Signed-In Owner Account", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = userEmail.ifBlank { "Authenticated Cafe Owner" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { signOut(credentialManager, scope) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}
