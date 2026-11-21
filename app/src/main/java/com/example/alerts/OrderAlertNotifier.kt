package com.example.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CafeOrder
import com.example.util.formatMoney

object OrderAlertNotifier {

    const val CHANNEL_ID = "cafe_order_alerts"
    const val MONITOR_CHANNEL_ID = "cafe_background_listener"
    const val MONITOR_NOTIFICATION_ID = 4401
    const val TIMEOUT_NOTIFICATION_ID = 4402
    const val EXTRA_OPEN_ROUTE = "open_route"
    const val ROUTE_ORDERS = "orders"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val orderChannel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_orders_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_orders_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 140, 320)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(soundUri, audioAttributes)
                }
                manager.createNotificationChannel(orderChannel)
            }

            if (manager.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
                val monitorChannel = NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    context.getString(R.string.notification_channel_monitor_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_monitor_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(monitorChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildMonitorNotification(
        context: Context,
        statusText: String
    ): Notification {
        ensureNotificationChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, ROUTE_ORDERS)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            MONITOR_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, OrderMonitorService::class.java).apply {
            action = OrderMonitorService.ACTION_PAUSE_MONITOR
        }
        val pausePendingIntent = PendingIntent.getService(
            context,
            MONITOR_NOTIFICATION_ID + 1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, MONITOR_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Cafe Kitchen • Live Alerts Active")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open Orders", openPendingIntent)
            .addAction(0, "Pause", pausePendingIntent)
            .build()
    }

    fun notifyNewOrder(
        context: Context,
        order: CafeOrder,
        currencySymbol: String,
        playSound: Boolean
    ) {
        ensureNotificationChannel(context)
        if (playSound) {
            playAlertChime(context)
            vibrateBriefly(context)
        }
        if (!hasNotificationPermission(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, ROUTE_ORDERS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            order.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val itemSummary = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
        val totalText = formatMoney(order.totalCents, currencySymbol)
        val title = "New Order ${order.orderNumber} • ${order.tableOrCustomer}"
        val body = "${order.parsedOrderType.label} • $totalText\n$itemSummary"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText("$totalText • ${order.totalItemCount} items")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(order.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between check and notify
        }
    }

    fun notifyOrderReady(
        context: Context,
        order: CafeOrder,
        currencySymbol: String
    ) {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, ROUTE_ORDERS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("ready_" + order.id).hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val totalText = formatMoney(order.totalCents, currencySymbol)
        val payHint = if (order.paymentStatus == "PAID") "Paid" else "Collect $totalText"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Order ${order.orderNumber} Ready for Pickup")
            .setContentText("${order.tableOrCustomer} • $payHint")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(("ready_" + order.id).hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked
        }
    }

    fun notifyMonitorTimeoutResume(context: Context) {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, ROUTE_ORDERS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TIMEOUT_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Tap to Resume Instant 4s Order Alerts")
            .setContentText("Android paused live sync after 6 hours in background. Periodic checks stay active.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(TIMEOUT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Ignore
        }
    }

    fun playAlertChime(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            ringtone?.play()
        } catch (_: Exception) {
            // Ignore audio playback issues on headless or muted devices
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateBriefly(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 240, 120, 280), -1)
                    )
                } else {
                    vibrator.vibrate(longArrayOf(0, 240, 120, 280), -1)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration failures
        }
    }
}
