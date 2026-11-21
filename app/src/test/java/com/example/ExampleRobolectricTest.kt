package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.alerts.OrderAlertNotifier
import com.example.alerts.OrderMonitorPreferences
import com.example.alerts.OrderSyncJobScheduler
import com.example.data.model.CafeOrder
import com.example.data.model.OrderStatus
import com.example.util.buildTableOrderingUrl
import com.example.util.extractTableCodeFromDeepLink
import com.example.util.extractTableNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Cafe Kitchen", appName)
    }

    @Test
    fun `buildTableOrderingUrl produces dynamic ample table hash URL`() {
        assertEquals(
            "https://gdp4fzx3f5u6.ample.run/#table-3",
            buildTableOrderingUrl("https://gdp4fzx3f5u6.ample.run/kitchen#kitchen", "T3")
        )
        assertEquals(
            "https://gdp4fzx3f5u6.ample.run/#table-12",
            buildTableOrderingUrl("https://gdp4fzx3f5u6.ample.run/#table-3", "12")
        )
    }

    @Test
    fun `extractTableCodeFromDeepLink parses ample hash and cafekitchen scheme`() {
        assertEquals(
            "3",
            extractTableCodeFromDeepLink(Uri.parse("https://gdp4fzx3f5u6.ample.run/#table-3"))
        )
        assertEquals(
            "5",
            extractTableCodeFromDeepLink(Uri.parse("cafekitchen://order?table=5"))
        )
        assertEquals("3", extractTableNumber("T3"))
    }

    @Test
    fun `claimNewOrdersForNotification alerts once per new order and skips seeded or served orders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "owner_test_${System.currentTimeMillis()}"
        OrderMonitorPreferences.seedKnownOrders(context, uid, listOf("ample_existing"))

        val incomingBatch = listOf(
            CafeOrder(id = "ample_existing", status = OrderStatus.NEW.name),
            CafeOrder(id = "ample_fresh_1", status = OrderStatus.NEW.name),
            CafeOrder(id = "ample_old_served", status = OrderStatus.COMPLETED.name)
        )

        val firstClaim = OrderMonitorPreferences.claimNewOrdersForNotification(context, uid, incomingBatch)
        assertEquals(listOf("ample_fresh_1"), firstClaim.map { it.id })

        // Subsequent poll with same orders should not duplicate notifications
        val secondClaim = OrderMonitorPreferences.claimNewOrdersForNotification(context, uid, incomingBatch)
        assertTrue(secondClaim.isEmpty())
    }

    @Test
    fun `notification channels and job scheduler initialize cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OrderAlertNotifier.ensureNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        assertTrue(manager.getNotificationChannel(OrderAlertNotifier.CHANNEL_ID) != null)
        assertTrue(manager.getNotificationChannel(OrderAlertNotifier.MONITOR_CHANNEL_ID) != null)

        OrderSyncJobScheduler.schedule(context)
        OrderSyncJobScheduler.cancel(context)
    }
}
