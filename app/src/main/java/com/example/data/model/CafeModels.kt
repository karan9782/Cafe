package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OrderType(val label: String) {
    QR_SCAN("QR Table Order"),
    DINE_IN("Dine-In"),
    TAKEAWAY("Takeaway"),
    ONLINE("Web / Online");

    companion object {
        fun fromRaw(raw: String?): OrderType =
            entries.firstOrNull { it.name == raw } ?: QR_SCAN
    }
}

enum class OrderStatus(val label: String) {
    NEW("New"),
    PREPARING("Preparing"),
    READY("Ready"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name == raw } ?: NEW
    }
}

enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PAID("Paid");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name == raw } ?: UNPAID
    }
}

enum class PaymentMethod(val label: String) {
    NONE("None"),
    CASH("Cash"),
    CARD("Card"),
    UPI("UPI / QR"),
    SPLIT("Split");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class MenuCategory(val label: String) {
    COFFEE("Coffee & Chai"),
    BAKERY("Bakery"),
    KITCHEN("Kitchen"),
    COLD_DRINKS("Cold Drinks");

    companion object {
        fun fromRaw(raw: String?): MenuCategory =
            entries.firstOrNull { it.name == raw } ?: COFFEE
    }
}

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Long = 1L,
    val unitPriceCents: Long = 0L,
    val notes: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "menuItemId" to menuItemId,
        "name" to name,
        "quantity" to quantity,
        "unitPriceCents" to unitPriceCents,
        "notes" to notes
    )
}

@Keep
@IgnoreExtraProperties
data class CafeOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderNumber: String = "",
    val tableOrCustomer: String = "",
    val orderType: String = OrderType.QR_SCAN.name,
    val status: String = OrderStatus.NEW.name,
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val paymentMethod: String = PaymentMethod.NONE.name,
    val items: List<OrderLineItem> = emptyList(),
    val subtotalCents: Long = 0L,
    val taxCents: Long = 0L,
    val tipCents: Long = 0L,
    val discountCents: Long = 0L,
    val totalCents: Long = 0L,
    val notes: String = "",
    val paymentReference: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val paidAt: Timestamp? = null
) {
    val parsedOrderType: OrderType get() = OrderType.fromRaw(orderType)
    val parsedStatus: OrderStatus get() = OrderStatus.fromRaw(status)
    val parsedPaymentStatus: PaymentStatus get() = PaymentStatus.fromRaw(paymentStatus)
    val parsedPaymentMethod: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)
    val totalItemCount: Long get() = items.sumOf { it.quantity }
}

@Keep
@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = MenuCategory.COFFEE.name,
    val priceCents: Long = 0L,
    val prepMinutes: Long = 5L,
    val available: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedCategory: MenuCategory get() = MenuCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class QrTableStation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val tableCode: String = "",
    val label: String = "",
    val linkedUrl: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CafeSettings(
    val userId: String = "",
    val cafeName: String = "Mawa’s Cafe",
    val websiteUrl: String = "https://gdp4fzx3f5u6.ample.run/kitchen#kitchen",
    val upiId: String = "",
    val currencySymbol: String = "₹",
    val taxRatePercent: Double = 0.0,
    val alertsEnabled: Boolean = true,
    val soundAlertsEnabled: Boolean = true,
    val nextOrderCounter: Long = 101L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CartEntry(
    val menuItem: MenuItem,
    val quantity: Long,
    val notes: String = ""
)

sealed interface PortalSyncStatus {
    data object Disconnected : PortalSyncStatus
    data object Connecting : PortalSyncStatus
    data class Connected(val lastSyncMillis: Long, val portalOrdersCount: Int) : PortalSyncStatus
    data class Error(val message: String) : PortalSyncStatus
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
