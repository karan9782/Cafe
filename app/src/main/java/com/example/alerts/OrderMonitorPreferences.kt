package com.example.alerts

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CafeOrder
import com.example.data.model.OrderStatus

object OrderMonitorPreferences {
    private const val PREFS_NAME = "ample_portal_prefs"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_PORTAL_URL = "portal_url"
    private const val KEY_CURRENCY_SYMBOL = "currency_symbol"
    private const val KEY_ALERTS_ENABLED = "alerts_enabled"
    private const val KEY_SOUND_ENABLED = "sound_alerts_enabled"
    private const val KEY_BG_MONITOR_ENABLED = "bg_monitor_enabled"
    private const val KEY_ARMED_AT_MILLIS = "monitor_armed_at_millis"
    private const val KEY_LAST_BG_SYNC_MILLIS = "last_bg_sync_millis"
    private const val KEY_LAST_BG_SYNC_ERROR = "last_bg_sync_error"
    private const val KEY_MENU_PRICE_CACHE = "menu_price_cache_json"

    private const val DEFAULT_PORTAL_URL = "https://gdp4fzx3f5u6.ample.run/kitchen#kitchen"
    private const val MAX_TRACKED_ORDER_IDS = 300

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun staffKeyPrefKey(userId: String): String = "staff_key_$userId"
    private fun knownOrdersKey(userId: String): String = "known_order_ids_$userId"
    private fun knownSeededKey(userId: String): String = "known_orders_seeded_$userId"

    @Synchronized
    fun setActiveUser(context: Context, userId: String) {
        val clean = userId.trim()
        if (clean.isEmpty()) return
        val p = prefs(context)
        val editor = p.edit().putString(KEY_ACTIVE_USER_ID, clean)
        if (!p.contains(KEY_ARMED_AT_MILLIS)) {
            editor.putLong(KEY_ARMED_AT_MILLIS, System.currentTimeMillis())
        }
        editor.apply()
    }

    @Synchronized
    fun getActiveUserId(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_USER_ID, "").orEmpty()

    @Synchronized
    fun saveStaffKey(context: Context, userId: String, staffKey: String) {
        val cleanKey = staffKey.trim()
        prefs(context).edit()
            .putString(KEY_ACTIVE_USER_ID, userId)
            .putString(staffKeyPrefKey(userId), cleanKey)
            .putLong(KEY_ARMED_AT_MILLIS, System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun clearStaffKey(context: Context, userId: String) {
        prefs(context).edit()
            .remove(staffKeyPrefKey(userId))
            .apply()
    }

    @Synchronized
    fun getStaffKey(context: Context, userId: String = getActiveUserId(context)): String {
        if (userId.isBlank()) return ""
        return prefs(context).getString(staffKeyPrefKey(userId), "").orEmpty()
    }

    @Synchronized
    fun saveAlertSettings(
        context: Context,
        portalUrl: String,
        currencySymbol: String,
        alertsEnabled: Boolean,
        soundAlertsEnabled: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_PORTAL_URL, portalUrl.ifBlank { DEFAULT_PORTAL_URL })
            .putString(KEY_CURRENCY_SYMBOL, currencySymbol.ifBlank { "₹" })
            .putBoolean(KEY_ALERTS_ENABLED, alertsEnabled)
            .putBoolean(KEY_SOUND_ENABLED, soundAlertsEnabled)
            .apply()
    }

    @Synchronized
    fun setBackgroundMonitorEnabled(context: Context, enabled: Boolean) {
        val editor = prefs(context).edit()
            .putBoolean(KEY_BG_MONITOR_ENABLED, enabled)
        if (enabled) {
            editor.putLong(KEY_ARMED_AT_MILLIS, System.currentTimeMillis())
        }
        editor.apply()
    }

    @Synchronized
    fun isBackgroundMonitorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BG_MONITOR_ENABLED, true)

    @Synchronized
    fun areAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALERTS_ENABLED, true)

    @Synchronized
    fun isSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND_ENABLED, true)

    @Synchronized
    fun getPortalUrl(context: Context): String =
        prefs(context).getString(KEY_PORTAL_URL, DEFAULT_PORTAL_URL).orEmpty()
            .ifBlank { DEFAULT_PORTAL_URL }

    @Synchronized
    fun getCurrencySymbol(context: Context): String =
        prefs(context).getString(KEY_CURRENCY_SYMBOL, "₹").orEmpty().ifBlank { "₹" }

    @Synchronized
    fun saveMenuPriceCache(context: Context, priceCacheJson: String) {
        prefs(context).edit().putString(KEY_MENU_PRICE_CACHE, priceCacheJson).apply()
    }

    @Synchronized
    fun getMenuPriceCache(context: Context): String =
        prefs(context).getString(KEY_MENU_PRICE_CACHE, "").orEmpty()

    @Synchronized
    fun recordBackgroundSyncResult(context: Context, errorMessage: String?) {
        val editor = prefs(context).edit()
        if (errorMessage == null) {
            editor.putLong(KEY_LAST_BG_SYNC_MILLIS, System.currentTimeMillis())
                .remove(KEY_LAST_BG_SYNC_ERROR)
        } else {
            editor.putString(KEY_LAST_BG_SYNC_ERROR, errorMessage)
        }
        editor.apply()
    }

    @Synchronized
    fun getLastBackgroundSyncMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BG_SYNC_MILLIS, 0L)

    @Synchronized
    fun isSeeded(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs(context).getBoolean(knownSeededKey(userId), false)
    }

    @Synchronized
    fun seedKnownOrders(context: Context, userId: String, orderIds: Collection<String>) {
        if (userId.isBlank()) return
        val p = prefs(context)
        val existing = p.getStringSet(knownOrdersKey(userId), emptySet()).orEmpty().toMutableSet()
        existing.addAll(orderIds.filter { it.isNotBlank() })
        val trimmed = trimSet(existing)
        p.edit()
            .putStringSet(knownOrdersKey(userId), trimmed)
            .putBoolean(knownSeededKey(userId), true)
            .apply()
    }

    /**
     * Atomically compares incoming orders against persisted known IDs so an order only triggers
     * a push notification + sound chime once, across both the foreground UI and background service.
     */
    @Synchronized
    fun claimNewOrdersForNotification(
        context: Context,
        userId: String,
        orders: List<CafeOrder>
    ): List<CafeOrder> {
        if (userId.isBlank() || orders.isEmpty()) return emptyList()
        val p = prefs(context)
        val wasSeeded = p.getBoolean(knownSeededKey(userId), false)
        val armedAt = p.getLong(KEY_ARMED_AT_MILLIS, 0L)
        val existing = p.getStringSet(knownOrdersKey(userId), emptySet()).orEmpty().toMutableSet()

        val newlyArrived = orders.filter { order ->
            val unseen = order.id.isNotBlank() && order.id !in existing
            if (!unseen || order.parsedStatus != OrderStatus.NEW) {
                false
            } else if (wasSeeded) {
                true
            } else {
                val createdMillis = order.createdAt?.toDate()?.time ?: 0L
                armedAt > 0L && createdMillis >= (armedAt - 5_000L)
            }
        }

        orders.forEach { if (it.id.isNotBlank()) existing.add(it.id) }
        p.edit()
            .putStringSet(knownOrdersKey(userId), trimSet(existing))
            .putBoolean(knownSeededKey(userId), true)
            .apply()

        return newlyArrived
    }

    private fun trimSet(source: Set<String>): Set<String> {
        if (source.size <= MAX_TRACKED_ORDER_IDS) return source.toSet()
        return source.toList().takeLast(MAX_TRACKED_ORDER_IDS).toSet()
    }
}
