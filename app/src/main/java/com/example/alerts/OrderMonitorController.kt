package com.example.alerts

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object OrderMonitorController {
    private const val TAG = "OrderMonitorController"

    fun syncMonitoringState(context: Context) {
        val appContext = context.applicationContext
        val alertsEnabled = OrderMonitorPreferences.areAlertsEnabled(appContext)
        val bgEnabled = OrderMonitorPreferences.isBackgroundMonitorEnabled(appContext)
        val userId = OrderMonitorPreferences.getActiveUserId(appContext)
        val staffKey = OrderMonitorPreferences.getStaffKey(appContext, userId)

        if (alertsEnabled && bgEnabled && userId.isNotBlank() &&
            (staffKey.isNotBlank() || OrderAlertNotifier.hasNotificationPermission(appContext))
        ) {
            if (staffKey.isNotBlank()) {
                OrderSyncJobScheduler.schedule(appContext)
            }
            if (AppVisibilityTracker.isAppInForeground) {
                try {
                    ContextCompat.startForegroundService(
                        appContext,
                        Intent(appContext, OrderMonitorService::class.java)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start OrderMonitorService", e)
                }
            }
        } else {
            stopMonitoring(appContext)
        }
    }

    fun triggerDelayedBackgroundTest(context: Context): Boolean {
        val appContext = context.applicationContext
        OrderMonitorPreferences.setBackgroundMonitorEnabled(appContext, true)
        return try {
            val intent = Intent(appContext, OrderMonitorService::class.java).apply {
                action = OrderMonitorService.ACTION_SCHEDULE_TEST_DELAYED
            }
            ContextCompat.startForegroundService(appContext, intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule delayed background test alert", e)
            false
        }
    }

    fun stopMonitoring(context: Context) {
        val appContext = context.applicationContext
        OrderSyncJobScheduler.cancel(appContext)
        runCatching {
            appContext.stopService(Intent(appContext, OrderMonitorService::class.java))
        }
    }
}
