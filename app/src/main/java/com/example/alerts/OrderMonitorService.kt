package com.example.alerts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.data.CafeRepository
import com.example.data.model.CafeOrder
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.remote.AmpleApiException
import com.example.data.remote.AmpleBackendClient
import com.example.data.remote.PortalOrderMapper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OrderMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ampleClient = AmpleBackendClient()

    private var pollingJob: Job? = null
    private var delayedTestJob: Job? = null
    private var firestoreListener: ListenerRegistration? = null
    private var listeningUserId: String? = null
    private var priceLookup: Map<String, Double> = emptyMap()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE_MONITOR) {
            OrderMonitorPreferences.setBackgroundMonitorEnabled(applicationContext, false)
            OrderSyncJobScheduler.cancel(applicationContext)
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val alertsEnabled = OrderMonitorPreferences.areAlertsEnabled(applicationContext)
        val bgEnabled = OrderMonitorPreferences.isBackgroundMonitorEnabled(applicationContext)
        val userId = OrderMonitorPreferences.getActiveUserId(applicationContext)
            .ifBlank { Firebase.auth.currentUser?.uid.orEmpty() }

        val initialText = if (OrderMonitorPreferences.getStaffKey(applicationContext, userId).isNotBlank()) {
            "Listening for QR orders (even when app is closed)"
        } else {
            "Listening for Firestore orders (link Staff Key for portal sync)"
        }
        startForegroundCompat(initialText)

        if (intent?.action == ACTION_SCHEDULE_TEST_DELAYED) {
            scheduleDelayedTestNotification()
        }

        if (!alertsEnabled || !bgEnabled || userId.isBlank()) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        OrderMonitorPreferences.setActiveUser(applicationContext, userId)
        OrderSyncJobScheduler.schedule(applicationContext)
        ensureFirestoreOrderListener(userId)
        ensurePortalPollingLoop()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val ctx = applicationContext
        if (OrderMonitorPreferences.areAlertsEnabled(ctx) &&
            OrderMonitorPreferences.isBackgroundMonitorEnabled(ctx)
        ) {
            OrderSyncJobScheduler.schedule(ctx)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "dataSync FGS reached Android 15+ timeout; switching to JobScheduler backup.")
        OrderSyncJobScheduler.schedule(applicationContext)
        OrderAlertNotifier.notifyMonitorTimeoutResume(applicationContext)
        stopForegroundAndSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        firestoreListener = null
        pollingJob?.cancel()
        delayedTestJob?.cancel()
        serviceScope.cancel()
    }

    private fun startForegroundCompat(statusText: String) {
        val notification = OrderAlertNotifier.buildMonitorNotification(applicationContext, statusText)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            OrderAlertNotifier.MONITOR_NOTIFICATION_ID,
            notification,
            type
        )
    }

    private fun stopForegroundAndSelf() {
        firestoreListener?.remove()
        firestoreListener = null
        pollingJob?.cancel()
        pollingJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleDelayedTestNotification() {
        delayedTestJob?.cancel()
        delayedTestJob = serviceScope.launch {
            delay(5000L)
            val ctx = applicationContext
            val currency = OrderMonitorPreferences.getCurrencySymbol(ctx)
            val sound = OrderMonitorPreferences.isSoundEnabled(ctx)
            val sample = CafeOrder(
                id = "bg_test_${System.currentTimeMillis()}",
                orderNumber = "#TEST",
                tableOrCustomer = "Table 3 • Background Test",
                orderType = OrderType.QR_SCAN.name,
                status = OrderStatus.NEW.name,
                items = listOf(
                    OrderLineItem(
                        name = "Cold Coffee",
                        quantity = 1L,
                        unitPriceCents = 5900L
                    )
                ),
                totalCents = 5900L
            )
            OrderAlertNotifier.notifyNewOrder(
                context = ctx,
                order = sample,
                currencySymbol = currency,
                playSound = sound
            )
        }
    }

    private fun ensureFirestoreOrderListener(userId: String) {
        if (listeningUserId == userId && firestoreListener != null) return
        val firebaseUser = Firebase.auth.currentUser
        if (firebaseUser == null || firebaseUser.uid != userId) return

        firestoreListener?.remove()
        listeningUserId = userId

        val dbId = applicationContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(dbId)
        firestoreListener = db.collection("users").document(userId)
            .collection("orders")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val ctx = applicationContext
                val orders = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CafeOrder::class.java)?.copy(id = doc.id)
                }
                if (orders.isEmpty()) return@addSnapshotListener

                val newOrders = OrderMonitorPreferences.claimNewOrdersForNotification(
                    context = ctx,
                    userId = userId,
                    orders = orders
                )
                if (!AppVisibilityTracker.isAppInForeground &&
                    OrderMonitorPreferences.areAlertsEnabled(ctx)
                ) {
                    val currency = OrderMonitorPreferences.getCurrencySymbol(ctx)
                    val sound = OrderMonitorPreferences.isSoundEnabled(ctx)
                    newOrders.forEach { order ->
                        OrderAlertNotifier.notifyNewOrder(
                            context = ctx,
                            order = order,
                            currencySymbol = currency,
                            playSound = sound
                        )
                    }
                }
            }
    }

    private fun ensurePortalPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = serviceScope.launch {
            while (isActive) {
                val ctx = applicationContext
                if (!OrderMonitorPreferences.areAlertsEnabled(ctx) ||
                    !OrderMonitorPreferences.isBackgroundMonitorEnabled(ctx)
                ) {
                    stopForegroundAndSelf()
                    break
                }

                val userId = OrderMonitorPreferences.getActiveUserId(ctx)
                val staffKey = OrderMonitorPreferences.getStaffKey(ctx, userId)
                val portalUrl = OrderMonitorPreferences.getPortalUrl(ctx)

                // Only poll from background service when UI is minimized or closed;
                // when app is in foreground, CafeViewModel handles 3s live polling.
                if (!AppVisibilityTracker.isAppInForeground && userId.isNotBlank() && staffKey.isNotBlank()) {
                    ensureMenuPriceLookup(portalUrl)
                    val res = ampleClient.fetchKitchenOrders(
                        portalUrl = portalUrl,
                        staffKey = staffKey,
                        sinceMillis = PortalOrderMapper.startOfDayMillis()
                    )
                    res.onSuccess { dtos ->
                        val mapped = dtos.map {
                            PortalOrderMapper.mapAmpleOrderToCafeOrder(it, userId, priceLookup)
                        }
                        OrderMonitorPreferences.recordBackgroundSyncResult(ctx, null)

                        val newOrders = OrderMonitorPreferences.claimNewOrdersForNotification(
                            context = ctx,
                            userId = userId,
                            orders = mapped
                        )
                        val currency = OrderMonitorPreferences.getCurrencySymbol(ctx)
                        val sound = OrderMonitorPreferences.isSoundEnabled(ctx)
                        newOrders.forEach { order ->
                            OrderAlertNotifier.notifyNewOrder(
                                context = ctx,
                                order = order,
                                currencySymbol = currency,
                                playSound = sound
                            )
                        }

                        val firebaseUser = Firebase.auth.currentUser
                        if (firebaseUser != null && firebaseUser.uid == userId && mapped.isNotEmpty()) {
                            val dbId = ctx.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(dbId)
                            CafeRepository(db).syncPortalOrdersFromBackground(userId, mapped)
                        }
                    }.onFailure { err ->
                        OrderMonitorPreferences.recordBackgroundSyncResult(ctx, err.localizedMessage)
                        if (err is AmpleApiException && err.statusCode == 401) {
                            OrderMonitorPreferences.clearStaffKey(ctx, userId)
                            Log.w(TAG, "Staff key rejected in background service; stopping portal poll.")
                        }
                    }
                }
                delay(BG_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun ensureMenuPriceLookup(portalUrl: String) {
        if (priceLookup.isNotEmpty()) return
        val cached = OrderMonitorPreferences.getMenuPriceCache(applicationContext)
        if (cached.isNotBlank()) {
            priceLookup = PortalOrderMapper.deserializePriceLookup(cached)
            if (priceLookup.isNotEmpty()) return
        }
        ampleClient.fetchMenuAndTables(portalUrl).onSuccess { payload ->
            val built = PortalOrderMapper.buildPriceLookup(payload.categories)
            if (built.isNotEmpty()) {
                priceLookup = built
                OrderMonitorPreferences.saveMenuPriceCache(
                    applicationContext,
                    PortalOrderMapper.serializePriceLookup(built)
                )
            }
        }
    }

    companion object {
        private const val TAG = "OrderMonitorService"
        private const val BG_POLL_INTERVAL_MS = 4000L
        const val ACTION_PAUSE_MONITOR = "com.example.alerts.ACTION_PAUSE_MONITOR"
        const val ACTION_SCHEDULE_TEST_DELAYED = "com.example.alerts.ACTION_SCHEDULE_TEST_DELAYED"
    }
}
