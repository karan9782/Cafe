package com.example.alerts

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.CafeRepository
import com.example.data.remote.AmpleBackendClient
import com.example.data.remote.PortalOrderMapper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object OrderSyncJobScheduler {
    private const val JOB_ID = 4409
    private const val PERIODIC_INTERVAL_MS = 15L * 60L * 1000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val existing = scheduler.allPendingJobs.any { it.id == JOB_ID }
        if (existing) return

        val component = ComponentName(appContext, OrderSyncJobService::class.java)
        val info = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(PERIODIC_INTERVAL_MS)
            .build()
        runCatching { scheduler.schedule(info) }
    }

    fun cancel(context: Context) {
        val scheduler = context.applicationContext
            .getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        scheduler.cancel(JOB_ID)
    }
}

class OrderSyncJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val ctx = applicationContext
        val alertsEnabled = OrderMonitorPreferences.areAlertsEnabled(ctx)
        val bgEnabled = OrderMonitorPreferences.isBackgroundMonitorEnabled(ctx)
        val userId = OrderMonitorPreferences.getActiveUserId(ctx)
        val staffKey = OrderMonitorPreferences.getStaffKey(ctx, userId)

        if (!alertsEnabled || !bgEnabled || userId.isBlank() || staffKey.isBlank()) {
            return false
        }

        activeJob?.cancel()
        activeJob = serviceScope.launch {
            try {
                val portalUrl = OrderMonitorPreferences.getPortalUrl(ctx)
                val currency = OrderMonitorPreferences.getCurrencySymbol(ctx)
                val soundEnabled = OrderMonitorPreferences.isSoundEnabled(ctx)
                val priceLookup = PortalOrderMapper.deserializePriceLookup(
                    OrderMonitorPreferences.getMenuPriceCache(ctx)
                )

                val client = AmpleBackendClient()
                val res = client.fetchKitchenOrders(
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
                    newOrders.forEach { order ->
                        OrderAlertNotifier.notifyNewOrder(
                            context = ctx,
                            order = order,
                            currencySymbol = currency,
                            playSound = soundEnabled
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
                    Log.w(TAG, "Periodic background sync failed", err)
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private companion object {
        const val TAG = "OrderSyncJobService"
    }
}
