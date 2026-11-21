package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.alerts.AppVisibilityTracker
import com.example.alerts.OrderAlertNotifier
import com.example.alerts.OrderMonitorController
import com.example.ui.navigation.CafeAppRoot
import com.example.ui.theme.CafeKitchenTheme
import com.example.util.extractTableCodeFromDeepLink

class MainActivity : ComponentActivity() {
    private var deepLinkTableCode by mutableStateOf<String?>(null)
    private var openOrdersNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OrderAlertNotifier.ensureNotificationChannel(this)
        extractIntentExtras(intent)

        setContent {
            CafeKitchenTheme {
                CafeAppRoot(
                    deepLinkTableCode = deepLinkTableCode,
                    onConsumeDeepLink = { deepLinkTableCode = null },
                    openOrdersNonce = openOrdersNonce
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibilityTracker.setForeground(true)
        OrderMonitorController.syncMonitoringState(this)
    }

    override fun onStop() {
        AppVisibilityTracker.setForeground(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentExtras(intent)
    }

    private fun extractIntentExtras(intent: Intent?) {
        val extracted = extractTableCodeFromDeepLink(intent?.data)
        if (!extracted.isNullOrEmpty()) {
            deepLinkTableCode = extracted
        }
        if (intent?.getStringExtra(OrderAlertNotifier.EXTRA_OPEN_ROUTE) == OrderAlertNotifier.ROUTE_ORDERS) {
            openOrdersNonce++
        }
    }
}
