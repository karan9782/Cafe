package com.example.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val alertsEnabled = OrderMonitorPreferences.areAlertsEnabled(context)
            val bgEnabled = OrderMonitorPreferences.isBackgroundMonitorEnabled(context)
            val userId = OrderMonitorPreferences.getActiveUserId(context)
            val staffKey = OrderMonitorPreferences.getStaffKey(context, userId)
            if (alertsEnabled && bgEnabled && userId.isNotBlank() && staffKey.isNotBlank()) {
                OrderSyncJobScheduler.schedule(context)
            }
        }
    }
}
