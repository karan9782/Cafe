package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alerts.OrderAlertNotifier
import com.example.alerts.OrderMonitorController
import com.example.alerts.OrderMonitorPreferences
import com.example.data.CafeRepository
import com.example.data.model.CafeOrder
import com.example.data.model.CafeSettings
import com.example.data.model.CartEntry
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.PortalSyncStatus
import com.example.data.model.QrTableStation
import com.example.data.model.UiState
import com.example.data.remote.AmpleApiException
import com.example.data.remote.AmpleBackendClient
import com.example.data.remote.AmpleMenuCategoryDto
import com.example.data.remote.AmpleMenuItemDto
import com.example.data.remote.AmpleOrderDto
import com.example.data.remote.PortalOrderMapper
import com.example.util.buildTableOrderingUrl
import com.example.util.extractTableNumber
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AmpleCartEntry(
    val item: AmpleMenuItemDto,
    val quantity: Long
)

class CafeViewModel(
    private val repository: CafeRepository,
    private val currentUserId: String,
    private val appContext: Context,
    private val defaultPortalUrl: String,
    private val ampleClient: AmpleBackendClient = AmpleBackendClient()
) : ViewModel() {

    private var hasHydratedInitialOrders = false
    private val knownOrderIds = mutableSetOf<String>()
    private var pollingJob: Job? = null
    private var hasSyncedInitialTables = false

    private val _latestIncomingAlertOrder = MutableStateFlow<CafeOrder?>(null)
    val latestIncomingAlertOrder: StateFlow<CafeOrder?> = _latestIncomingAlertOrder.asStateFlow()

    private val _cart = MutableStateFlow<Map<String, CartEntry>>(emptyMap())
    val cart: StateFlow<Map<String, CartEntry>> = _cart.asStateFlow()

    private val _ampleCart = MutableStateFlow<Map<String, AmpleCartEntry>>(emptyMap())
    val ampleCart: StateFlow<Map<String, AmpleCartEntry>> = _ampleCart.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _staffKey = MutableStateFlow(OrderMonitorPreferences.getStaffKey(appContext, currentUserId))
    val staffKey: StateFlow<String> = _staffKey.asStateFlow()

    private val _backgroundMonitorEnabled = MutableStateFlow(
        OrderMonitorPreferences.isBackgroundMonitorEnabled(appContext)
    )
    val backgroundMonitorEnabled: StateFlow<Boolean> = _backgroundMonitorEnabled.asStateFlow()

    private val _portalSyncStatus = MutableStateFlow<PortalSyncStatus>(
        if (_staffKey.value.isNotBlank()) PortalSyncStatus.Connecting else PortalSyncStatus.Disconnected
    )
    val portalSyncStatus: StateFlow<PortalSyncStatus> = _portalSyncStatus.asStateFlow()

    private val _portalMenuState = MutableStateFlow<UiState<List<AmpleMenuCategoryDto>>>(UiState.Loading)
    val portalMenuState: StateFlow<UiState<List<AmpleMenuCategoryDto>>> = _portalMenuState.asStateFlow()

    private val _portalTableCount = MutableStateFlow(15)
    val portalTableCount: StateFlow<Int> = _portalTableCount.asStateFlow()

    private val _livePortalOrders = MutableStateFlow<List<CafeOrder>>(emptyList())
    private var latestFirestoreOrders: List<CafeOrder> = emptyList()

    val settingsState: StateFlow<CafeSettings> = repository.observeSettings(currentUserId)
        .map { it ?: CafeSettings(userId = currentUserId, websiteUrl = defaultPortalUrl) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing settings", firestoreError ?: error)
            emit(CafeSettings(userId = currentUserId, websiteUrl = defaultPortalUrl))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CafeSettings(userId = currentUserId, websiteUrl = defaultPortalUrl)
        )

    private val firestoreOrdersFlow = repository.observeOrders(currentUserId)
        .map<List<CafeOrder>, UiState<List<CafeOrder>>> { orders ->
            latestFirestoreOrders = orders
            UiState.Success(orders)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing orders", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load orders"))
        }

    val ordersState: StateFlow<UiState<List<CafeOrder>>> = combine(
        firestoreOrdersFlow,
        _livePortalOrders
    ) { fsState, portalOrders ->
        when (fsState) {
            is UiState.Loading -> {
                if (portalOrders.isNotEmpty()) {
                    detectNewOrderAlerts(portalOrders)
                    UiState.Success(portalOrders)
                } else {
                    UiState.Loading
                }
            }
            is UiState.Error -> {
                if (portalOrders.isNotEmpty()) {
                    detectNewOrderAlerts(portalOrders)
                    UiState.Success(portalOrders)
                } else {
                    fsState
                }
            }
            is UiState.Success -> {
                val merged = mergeOrders(fsState.data, portalOrders)
                detectNewOrderAlerts(merged)
                UiState.Success(merged)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val menuState: StateFlow<UiState<List<MenuItem>>> = repository.observeMenuItems(currentUserId)
        .map<List<MenuItem>, UiState<List<MenuItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing menu items", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load menu"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val qrTablesState: StateFlow<UiState<List<QrTableStation>>> = repository.observeQrTables(currentUserId)
        .map<List<QrTableStation>, UiState<List<QrTableStation>>> { tables ->
            val sorted = tables.sortedWith(
                compareBy(
                    { extractTableNumber(it.tableCode).toIntOrNull() ?: Int.MAX_VALUE },
                    { it.tableCode }
                )
            )
            UiState.Success(sorted)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing QR tables", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load QR tables"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        OrderMonitorPreferences.setActiveUser(appContext, currentUserId)
        viewModelScope.launch {
            repository.ensureDefaultSettings(currentUserId, defaultPortalUrl)
        }
        viewModelScope.launch {
            settingsState.collect { cfg ->
                OrderMonitorPreferences.saveAlertSettings(
                    context = appContext,
                    portalUrl = cfg.websiteUrl,
                    currencySymbol = cfg.currencySymbol,
                    alertsEnabled = cfg.alertsEnabled,
                    soundAlertsEnabled = cfg.soundAlertsEnabled
                )
                OrderMonitorController.syncMonitoringState(appContext)
            }
        }
        refreshPortalMenuAndTables(forceTableSync = false)
        if (_staffKey.value.isNotBlank()) {
            startPortalPolling()
        }
        OrderMonitorController.syncMonitoringState(appContext)
    }

    private fun mergeOrders(
        firestoreOrders: List<CafeOrder>,
        portalOrders: List<CafeOrder>
    ): List<CafeOrder> {
        if (portalOrders.isEmpty()) return firestoreOrders
        val mergedMap = LinkedHashMap<String, CafeOrder>()
        firestoreOrders.forEach { mergedMap[it.id] = it }
        portalOrders.forEach { portalOrder ->
            val existing = mergedMap[portalOrder.id]
            if (existing == null) {
                mergedMap[portalOrder.id] = portalOrder
            } else {
                // Keep payment settlement details from Firestore while reflecting live portal status if ahead
                val effectiveStatus = if (existing.parsedPaymentStatus == PaymentStatus.PAID &&
                    existing.parsedStatus == OrderStatus.COMPLETED
                ) {
                    existing.status
                } else if (statusRank(portalOrder.parsedStatus) > statusRank(existing.parsedStatus)) {
                    portalOrder.status
                } else {
                    existing.status
                }
                mergedMap[portalOrder.id] = existing.copy(status = effectiveStatus)
            }
        }
        return mergedMap.values.sortedByDescending {
            it.createdAt?.toDate()?.time ?: 0L
        }
    }

    private fun statusRank(status: OrderStatus): Int = when (status) {
        OrderStatus.NEW -> 0
        OrderStatus.PREPARING -> 1
        OrderStatus.READY -> 2
        OrderStatus.COMPLETED -> 3
        OrderStatus.CANCELLED -> 4
    }

    fun refreshPortalMenuAndTables(forceTableSync: Boolean = true) {
        viewModelScope.launch {
            if (_portalMenuState.value !is UiState.Success) {
                _portalMenuState.value = UiState.Loading
            }
            val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }
            val result = ampleClient.fetchMenuAndTables(websiteUrl)
            result.onSuccess { payload ->
                _portalMenuState.value = UiState.Success(payload.categories)
                _portalTableCount.value = payload.tables
                val priceMap = PortalOrderMapper.buildPriceLookup(payload.categories)
                if (priceMap.isNotEmpty()) {
                    OrderMonitorPreferences.saveMenuPriceCache(
                        appContext,
                        PortalOrderMapper.serializePriceLookup(priceMap)
                    )
                }
                if (forceTableSync || !hasSyncedInitialTables) {
                    hasSyncedInitialTables = true
                    val existingTables = runCatching {
                        (qrTablesState.first { it !is UiState.Loading } as? UiState.Success)?.data.orEmpty()
                    }.getOrDefault(emptyList())
                    repository.syncAmpleQrTables(
                        userId = currentUserId,
                        tableCount = payload.tables,
                        baseWebsiteUrl = websiteUrl,
                        existingTables = existingTables
                    )
                }
                if (forceTableSync) {
                    _snackbarMessages.tryEmit(
                        "Synced ${payload.categories.sumOf { it.items.size }} menu items & ${payload.tables} tables from ample.run."
                    )
                }
            }.onFailure { err ->
                if (_portalMenuState.value !is UiState.Success) {
                    _portalMenuState.value = UiState.Error(err.localizedMessage ?: "Failed to load portal menu")
                } else if (forceTableSync) {
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not refresh portal menu")
                }
            }
        }
    }

    fun connectStaffKey(rawKey: String) {
        val cleanKey = rawKey.trim()
        if (cleanKey.isEmpty()) {
            _snackbarMessages.tryEmit("Enter your portal staff key.")
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _portalSyncStatus.value = PortalSyncStatus.Connecting
            val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }
            val verifyResult = ampleClient.verifyStaffKey(websiteUrl, cleanKey)
            _isSubmitting.value = false
            verifyResult.onSuccess {
                OrderMonitorPreferences.saveStaffKey(appContext, currentUserId, cleanKey)
                _staffKey.value = cleanKey
                startPortalPolling()
                OrderMonitorController.syncMonitoringState(appContext)
                _snackbarMessages.tryEmit("Connected! Portal orders now sync live even when closed.")
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Invalid staff key"
                _portalSyncStatus.value = PortalSyncStatus.Error(msg)
                _snackbarMessages.tryEmit(msg)
            }
        }
    }

    fun adoptWebViewStaffKey(rawKey: String) {
        val cleanKey = rawKey.trim()
        if (cleanKey.isEmpty() || cleanKey == _staffKey.value) return
        viewModelScope.launch {
            val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }
            ampleClient.verifyStaffKey(websiteUrl, cleanKey).onSuccess {
                OrderMonitorPreferences.saveStaffKey(appContext, currentUserId, cleanKey)
                _staffKey.value = cleanKey
                startPortalPolling()
                OrderMonitorController.syncMonitoringState(appContext)
                _snackbarMessages.tryEmit("Auto-linked portal staff session for live Orders & Billing.")
            }
        }
    }

    fun disconnectStaffKey() {
        pollingJob?.cancel()
        pollingJob = null
        OrderMonitorPreferences.clearStaffKey(appContext, currentUserId)
        _staffKey.value = ""
        _livePortalOrders.value = emptyList()
        _portalSyncStatus.value = PortalSyncStatus.Disconnected
        OrderMonitorController.syncMonitoringState(appContext)
        _snackbarMessages.tryEmit("Disconnected portal order sync.")
    }

    fun setBackgroundMonitorEnabled(enabled: Boolean) {
        OrderMonitorPreferences.setBackgroundMonitorEnabled(appContext, enabled)
        _backgroundMonitorEnabled.value = enabled
        OrderMonitorController.syncMonitoringState(appContext)
        _snackbarMessages.tryEmit(
            if (enabled) {
                "Background order listener active (works even when app is closed)."
            } else {
                "Background order listener paused."
            }
        )
    }

    fun syncPortalOrdersNow() {
        if (_staffKey.value.isBlank()) {
            _snackbarMessages.tryEmit("Enter your portal staff key to sync orders.")
            return
        }
        viewModelScope.launch {
            pollPortalKitchenOnce(showErrors = true)
        }
    }

    private fun startPortalPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _staffKey.value.isNotBlank()) {
                pollPortalKitchenOnce(showErrors = false)
                delay(PORTAL_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollPortalKitchenOnce(showErrors: Boolean) {
        val key = _staffKey.value
        if (key.isBlank()) return
        val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }
        val result = ampleClient.fetchKitchenOrders(
            portalUrl = websiteUrl,
            staffKey = key,
            sinceMillis = PortalOrderMapper.startOfDayMillis()
        )
        result.onSuccess { dtos ->
            val mapped = dtos.map { mapAmpleOrderToCafeOrder(it) }
            _livePortalOrders.value = mapped
            _portalSyncStatus.value = PortalSyncStatus.Connected(
                lastSyncMillis = System.currentTimeMillis(),
                portalOrdersCount = mapped.size
            )
            val existingMap = latestFirestoreOrders.associateBy { it.id }
            repository.syncPortalOrdersToFirestore(
                userId = currentUserId,
                portalOrders = mapped,
                existingOrdersById = existingMap
            )
        }.onFailure { err ->
            if (err is AmpleApiException && err.statusCode == 401) {
                pollingJob?.cancel()
                OrderMonitorPreferences.clearStaffKey(appContext, currentUserId)
                _staffKey.value = ""
                _portalSyncStatus.value = PortalSyncStatus.Error("Staff key rejected by portal. Sign in again.")
                OrderMonitorController.syncMonitoringState(appContext)
                _snackbarMessages.tryEmit("Portal staff key expired or invalid.")
            } else {
                val msg = err.localizedMessage ?: "Portal sync interrupted"
                _portalSyncStatus.value = PortalSyncStatus.Error(msg)
                if (showErrors) {
                    _snackbarMessages.tryEmit(msg)
                }
            }
        }
    }

    private fun mapAmpleOrderToCafeOrder(dto: AmpleOrderDto): CafeOrder =
        PortalOrderMapper.mapAmpleOrderToCafeOrder(
            dto = dto,
            userId = currentUserId,
            priceLookup = buildMenuPriceLookup()
        )

    private fun buildMenuPriceLookup(): Map<String, Double> {
        val cats = (_portalMenuState.value as? UiState.Success)?.data.orEmpty()
        return PortalOrderMapper.buildPriceLookup(cats)
    }

    private fun detectNewOrderAlerts(orders: List<CafeOrder>) {
        if (!hasHydratedInitialOrders) {
            hasHydratedInitialOrders = true
            knownOrderIds.addAll(orders.map { it.id })
            if (orders.isNotEmpty()) {
                OrderMonitorPreferences.seedKnownOrders(appContext, currentUserId, orders.map { it.id })
            }
            return
        }
        val currentSettings = settingsState.value
        val newArrivals = orders.filter { it.id !in knownOrderIds }
        knownOrderIds.addAll(orders.map { it.id })

        val claimed = OrderMonitorPreferences.claimNewOrdersForNotification(
            context = appContext,
            userId = currentUserId,
            orders = orders
        )
        val alertCandidate = claimed.firstOrNull()
            ?: newArrivals.firstOrNull { it.parsedStatus == OrderStatus.NEW }
        if (alertCandidate != null && currentSettings.alertsEnabled) {
            _latestIncomingAlertOrder.value = alertCandidate
            claimed.forEach { order ->
                OrderAlertNotifier.notifyNewOrder(
                    context = appContext,
                    order = order,
                    currencySymbol = currentSettings.currencySymbol,
                    playSound = currentSettings.soundAlertsEnabled
                )
            }
        }
    }

    fun dismissIncomingAlert() {
        _latestIncomingAlertOrder.value = null
    }

    fun addAmpleItemToCart(item: AmpleMenuItemDto) {
        _ampleCart.update { current ->
            val existing = current[item.id]
            val nextQty = ((existing?.quantity ?: 0L) + 1L).coerceAtMost(20L)
            current + (item.id to AmpleCartEntry(item = item, quantity = nextQty))
        }
    }

    fun decrementAmpleItemFromCart(itemId: String) {
        _ampleCart.update { current ->
            val existing = current[itemId] ?: return@update current
            if (existing.quantity <= 1L) {
                current - itemId
            } else {
                current + (itemId to existing.copy(quantity = existing.quantity - 1L))
            }
        }
    }

    fun clearAmpleCart() {
        _ampleCart.value = emptyMap()
    }

    fun submitAmpleCustomerQrOrder(
        tableCode: String,
        customerName: String,
        customerPhone: String,
        notes: String,
        adultConfirmed: Boolean,
        immediatePaymentMethod: PaymentMethod? = null,
        onSuccess: () -> Unit = {}
    ) {
        val entries = _ampleCart.value.values.toList()
        if (entries.isEmpty()) {
            _snackbarMessages.tryEmit("Add at least one item from the menu.")
            return
        }
        val cleanPhone = normalizeIndianPhone(customerPhone)
        if (cleanPhone.isEmpty()) {
            _snackbarMessages.tryEmit("Enter a valid 10-digit mobile number (starting with 6-9).")
            return
        }
        val cleanName = customerName.trim().ifEmpty { "Table Guest" }
        val tableNum = extractTableNumber(tableCode)
        val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }

        viewModelScope.launch {
            _isSubmitting.value = true
            val result = ampleClient.placeCustomerOrder(
                portalUrl = websiteUrl,
                tableNumber = tableNum,
                items = entries.map { it.item.id to it.quantity },
                note = notes,
                customerName = cleanName,
                customerPhone = cleanPhone,
                adultConfirmed = adultConfirmed
            )
            _isSubmitting.value = false
            result.onSuccess { createdDto ->
                _ampleCart.value = emptyMap()
                val mappedOrder = mapAmpleOrderToCafeOrder(createdDto)
                val existingMap = latestFirestoreOrders.associateBy { it.id }
                repository.syncPortalOrdersToFirestore(
                    userId = currentUserId,
                    portalOrders = listOf(mappedOrder),
                    existingOrdersById = existingMap
                )
                if (immediatePaymentMethod != null && immediatePaymentMethod != PaymentMethod.NONE) {
                    repository.processOrderPayment(
                        userId = currentUserId,
                        order = mappedOrder,
                        paymentMethod = immediatePaymentMethod,
                        tipCents = 0L,
                        discountCents = 0L,
                        paymentReference = "UPI QR Table $tableNum",
                        markOrderCompleted = false
                    )
                }
                _livePortalOrders.update { current ->
                    listOf(mappedOrder) + current.filterNot { it.id == mappedOrder.id }
                }
                _snackbarMessages.tryEmit("Order #${createdDto.number} placed for Table $tableNum!")
                if (_staffKey.value.isNotBlank()) {
                    pollPortalKitchenOnce(showErrors = false)
                }
                onSuccess()
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to place order on ample.run")
            }
        }
    }

    fun addToCart(item: MenuItem) {
        if (!item.available) {
            _snackbarMessages.tryEmit("${item.name} is currently sold out.")
            return
        }
        _cart.update { current ->
            val existing = current[item.id]
            val nextQty = ((existing?.quantity ?: 0L) + 1L).coerceAtMost(99L)
            current + (item.id to CartEntry(menuItem = item, quantity = nextQty, notes = existing?.notes.orEmpty()))
        }
    }

    fun decrementFromCart(itemId: String) {
        _cart.update { current ->
            val existing = current[itemId] ?: return@update current
            if (existing.quantity <= 1L) {
                current - itemId
            } else {
                current + (itemId to existing.copy(quantity = existing.quantity - 1L))
            }
        }
    }

    fun clearCart() {
        _cart.value = emptyMap()
    }

    fun submitQrCustomerOrder(
        tableLabel: String,
        customerName: String,
        notes: String,
        immediatePaymentMethod: PaymentMethod? = null,
        immediatePaymentReference: String = "",
        onSuccess: (orderId: String) -> Unit = {}
    ) {
        val entries = _cart.value.values.toList()
        if (entries.isEmpty()) {
            _snackbarMessages.tryEmit("Select at least one menu item to place the QR order.")
            return
        }
        val settings = settingsState.value
        val lineItems = entries.take(25).map { entry ->
            OrderLineItem(
                menuItemId = entry.menuItem.id,
                name = entry.menuItem.name,
                quantity = entry.quantity,
                unitPriceCents = entry.menuItem.priceCents,
                notes = entry.notes
            )
        }
        val displayCustomer = if (customerName.isNotBlank()) {
            "$tableLabel • ${customerName.trim()}"
        } else {
            tableLabel
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.createOrder(
                userId = currentUserId,
                orderNumberNumber = settings.nextOrderCounter,
                tableOrCustomer = displayCustomer,
                orderType = OrderType.QR_SCAN,
                items = lineItems,
                taxRatePercent = settings.taxRatePercent,
                notes = notes.ifBlank { "Placed via QR Table Scan" },
                immediatePaymentMethod = immediatePaymentMethod,
                immediatePaymentReference = immediatePaymentReference
            )
            _isSubmitting.value = false
            result.onSuccess { orderId ->
                _cart.value = emptyMap()
                _snackbarMessages.tryEmit("QR Order #${settings.nextOrderCounter} received!")
                onSuccess(orderId)
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to place QR order")
            }
        }
    }

    fun handleWebPortalBridgeOrder(
        tableLabel: String,
        itemSummary: String,
        totalRupees: Double
    ) {
        val cleanSummary = itemSummary.trim().take(60).ifEmpty { "Web Kitchen Order" }
        val cleanTable = tableLabel.trim().take(60).ifEmpty { "QR Web Order" }
        val totalPaise = (totalRupees.coerceIn(0.0, 100000.0) * 100.0).toLong().coerceAtLeast(100L)
        val settings = settingsState.value

        viewModelScope.launch {
            repository.createOrder(
                userId = currentUserId,
                orderNumberNumber = settings.nextOrderCounter,
                tableOrCustomer = cleanTable,
                orderType = OrderType.QR_SCAN,
                items = listOf(
                    OrderLineItem(
                        menuItemId = "web_portal",
                        name = cleanSummary,
                        quantity = 1L,
                        unitPriceCents = totalPaise,
                        notes = "Synced from Website"
                    )
                ),
                taxRatePercent = 0.0,
                notes = "Synced from linked QR website",
                immediatePaymentMethod = null
            ).onSuccess {
                _snackbarMessages.tryEmit("Synced new website order from $cleanTable")
            }
        }
    }

    fun advanceOrderStatus(order: CafeOrder, newStatus: OrderStatus) {
        viewModelScope.launch {
            val isAmpleOrder = order.id.startsWith("ample_")
            val ampleStatusToken = when (newStatus) {
                OrderStatus.NEW -> "new"
                OrderStatus.PREPARING -> "preparing"
                OrderStatus.READY -> "ready"
                OrderStatus.COMPLETED -> "served"
                OrderStatus.CANCELLED -> null
            }

            if (isAmpleOrder && ampleStatusToken != null && _staffKey.value.isNotBlank()) {
                val rawAmpleId = order.id.removePrefix("ample_")
                val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }
                val remoteRes = ampleClient.updateOrderStatus(
                    portalUrl = websiteUrl,
                    staffKey = _staffKey.value,
                    ampleOrderId = rawAmpleId,
                    ampleStatus = ampleStatusToken
                )
                if (remoteRes.isFailure) {
                    _snackbarMessages.tryEmit(
                        remoteRes.exceptionOrNull()?.localizedMessage ?: "Failed to update portal order"
                    )
                    return@launch
                }
                _livePortalOrders.update { list ->
                    list.map { if (it.id == order.id) it.copy(status = newStatus.name) else it }
                }
            }

            val result = repository.updateOrderStatus(currentUserId, order.id, newStatus)
            result.onSuccess {
                if (_latestIncomingAlertOrder.value?.id == order.id) {
                    _latestIncomingAlertOrder.value = null
                }
                val currentSettings = settingsState.value
                if (newStatus == OrderStatus.READY && currentSettings.alertsEnabled) {
                    OrderAlertNotifier.notifyOrderReady(
                        context = appContext,
                        order = order,
                        currencySymbol = currentSettings.currencySymbol
                    )
                }
                _snackbarMessages.tryEmit("Order ${order.orderNumber} marked ${newStatus.label}.")
            }.onFailure { err ->
                if (!isAmpleOrder) {
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update order status")
                }
            }
        }
    }

    fun processPayment(
        order: CafeOrder,
        method: PaymentMethod,
        tipCents: Long,
        discountCents: Long,
        paymentReference: String,
        markCompleted: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            // Ensure synced portal order exists in Firestore before updating payment
            if (order.id.startsWith("ample_") && latestFirestoreOrders.none { it.id == order.id }) {
                repository.syncPortalOrdersToFirestore(
                    userId = currentUserId,
                    portalOrders = listOf(order),
                    existingOrdersById = emptyMap()
                )
            }
            if (markCompleted && order.id.startsWith("ample_") && _staffKey.value.isNotBlank()) {
                val websiteUrl = settingsState.value.websiteUrl.ifBlank { defaultPortalUrl }
                ampleClient.updateOrderStatus(
                    portalUrl = websiteUrl,
                    staffKey = _staffKey.value,
                    ampleOrderId = order.id.removePrefix("ample_"),
                    ampleStatus = "served"
                )
            }
            val result = repository.processOrderPayment(
                userId = currentUserId,
                order = order,
                paymentMethod = method,
                tipCents = tipCents,
                discountCents = discountCents,
                paymentReference = paymentReference,
                markOrderCompleted = markCompleted
            )
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessages.tryEmit("Payment settled via ${method.label} for ${order.orderNumber}.")
                onSuccess()
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Payment failed")
            }
        }
    }

    fun cancelAndRemoveUnpaidOrder(order: CafeOrder) {
        viewModelScope.launch {
            val result = repository.updateOrderStatus(currentUserId, order.id, OrderStatus.CANCELLED)
            result.onSuccess {
                _snackbarMessages.tryEmit("Order ${order.orderNumber} cancelled.")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not cancel order")
            }
        }
    }

    fun addMenuItem(name: String, category: MenuCategory, priceCents: Long, prepMinutes: Long) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.addMenuItem(currentUserId, name, category, priceCents, prepMinutes)
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessages.tryEmit("Added $name to menu.")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to add menu item")
            }
        }
    }

    fun updateMenuItem(
        itemId: String,
        name: String,
        category: MenuCategory,
        priceCents: Long,
        prepMinutes: Long,
        available: Boolean
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.updateMenuItem(
                userId = currentUserId,
                itemId = itemId,
                name = name,
                category = category,
                priceCents = priceCents,
                prepMinutes = prepMinutes,
                available = available
            )
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessages.tryEmit("Updated $name.")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update item")
            }
        }
    }

    fun deleteMenuItem(item: MenuItem) {
        viewModelScope.launch {
            val result = repository.deleteMenuItem(currentUserId, item.id)
            result.onSuccess {
                _snackbarMessages.tryEmit("Removed ${item.name} from menu.")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to delete item")
            }
        }
    }

    fun loadStarterMenu() {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.seedStarterMenu(currentUserId)
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessages.tryEmit("Rupee cafe menu loaded.")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to load starter menu")
            }
        }
    }

    fun addQrTable(tableCode: String, label: String, customUrl: String?) {
        val baseUrl = settingsState.value.websiteUrl
        val url = customUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: buildTableOrderingUrl(baseUrl, tableCode)
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.addQrTable(currentUserId, tableCode, label, url)
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessages.tryEmit("Linked QR & Barcode for $label.")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to add QR table")
            }
        }
    }

    fun toggleQrTable(table: QrTableStation) {
        viewModelScope.launch {
            repository.toggleQrTableActive(currentUserId, table.id, !table.active)
        }
    }

    fun deleteQrTable(table: QrTableStation) {
        viewModelScope.launch {
            repository.deleteQrTable(currentUserId, table.id)
            _snackbarMessages.tryEmit("Removed ${table.label}.")
        }
    }

    fun loadDefaultQrTables() {
        refreshPortalMenuAndTables(forceTableSync = true)
    }

    fun saveSettings(
        cafeName: String,
        websiteUrl: String,
        upiId: String,
        currencySymbol: String,
        taxRatePercent: Double,
        alertsEnabled: Boolean,
        soundAlertsEnabled: Boolean
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.updateSettings(
                userId = currentUserId,
                cafeName = cafeName,
                websiteUrl = websiteUrl,
                upiId = upiId,
                currencySymbol = currencySymbol,
                taxRatePercent = taxRatePercent,
                alertsEnabled = alertsEnabled,
                soundAlertsEnabled = soundAlertsEnabled
            )
            _isSubmitting.value = false
            result.onSuccess {
                _snackbarMessages.tryEmit("Cafe settings saved.")
                refreshPortalMenuAndTables(forceTableSync = true)
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save settings")
            }
        }
    }

    fun sendTestOrderNotification() {
        val currentSettings = settingsState.value
        val sampleAlert = CafeOrder(
            id = "test_alert_${System.currentTimeMillis()}",
            orderNumber = "#${currentSettings.nextOrderCounter}",
            tableOrCustomer = "Table 3 (QR Scan)",
            orderType = OrderType.QR_SCAN.name,
            status = OrderStatus.NEW.name,
            items = listOf(OrderLineItem(name = "Cold Coffee", quantity = 2L, unitPriceCents = 5900L)),
            totalCents = 11800L
        )
        OrderAlertNotifier.notifyNewOrder(
            context = appContext,
            order = sampleAlert,
            currencySymbol = currentSettings.currencySymbol,
            playSound = currentSettings.soundAlertsEnabled
        )
        _snackbarMessages.tryEmit("Test QR order notification sent.")
    }

    fun sendDelayedClosedAppTestNotification() {
        OrderMonitorPreferences.setBackgroundMonitorEnabled(appContext, true)
        _backgroundMonitorEnabled.value = true
        val scheduled = OrderMonitorController.triggerDelayedBackgroundTest(appContext)
        _snackbarMessages.tryEmit(
            if (scheduled) {
                "Minimize or close the app now — test alert fires in 5 seconds."
            } else {
                "Enable notification permission to run background test."
            }
        )
    }

    private fun normalizeIndianPhone(input: String): String {
        var digits = input.filter { it.isDigit() }
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        return if (digits.length == 10 && digits[0] in '6'..'9') digits else ""
    }

    private companion object {
        const val TAG = "CafeViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val PORTAL_POLL_INTERVAL_MS = 3000L
    }
}
