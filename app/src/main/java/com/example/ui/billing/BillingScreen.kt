package com.example.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CafeOrder
import com.example.data.model.CafeSettings
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.UiState
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.OrderStatusBadge
import com.example.ui.components.PaymentStatusBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.formatMoney

private enum class BillingTab(val label: String) {
    UNPAID("Pending Bills"),
    PAID("Paid Receipts")
}

@Composable
fun BillingScreen(
    ordersState: UiState<List<CafeOrder>>,
    settings: CafeSettings,
    onCollectPayment: (CafeOrder) -> Unit,
    onViewReceipt: (CafeOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(BillingTab.UNPAID) }
    val currency = settings.currencySymbol

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (ordersState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = ordersState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val activeOrders = ordersState.data.filter { it.parsedStatus != OrderStatus.CANCELLED }
                val unpaidOrders = activeOrders.filter { it.parsedPaymentStatus == PaymentStatus.UNPAID }
                val paidOrders = activeOrders.filter { it.parsedPaymentStatus == PaymentStatus.PAID }

                val totalCollectedCents = paidOrders.sumOf { it.totalCents }
                val totalPendingCents = unpaidOrders.sumOf { it.totalCents }
                val cardCents = paidOrders.filter { it.parsedPaymentMethod == PaymentMethod.CARD }.sumOf { it.totalCents }
                val upiCents = paidOrders.filter { it.parsedPaymentMethod == PaymentMethod.UPI }.sumOf { it.totalCents }
                val cashCents = paidOrders.filter { it.parsedPaymentMethod == PaymentMethod.CASH }.sumOf { it.totalCents }

                // Summary Revenue Cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMetricCard(
                            title = "Collected (${paidOrders.size})",
                            value = formatMoney(totalCollectedCents, currency),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Pending (${unpaidOrders.size})",
                            value = formatMoney(totalPendingCents, currency),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Payment Channel Breakdown
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ChannelSummary("UPI/QR", formatMoney(upiCents, currency))
                            ChannelSummary("Cash", formatMoney(cashCents, currency))
                            ChannelSummary("Card", formatMoney(cardCents, currency))
                        }
                    }
                }

                // Filter Tabs
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BillingTab.entries.forEach { tab ->
                            val count = if (tab == BillingTab.UNPAID) unpaidOrders.size else paidOrders.size
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                label = { Text("${tab.label} ($count)") },
                                modifier = Modifier.testTag("billing_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                val list = if (selectedTab == BillingTab.UNPAID) unpaidOrders else paidOrders
                if (list.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (selectedTab == BillingTab.UNPAID) {
                                        "No Pending Customer Bills"
                                    } else {
                                        "No Paid Receipts Yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "QR table orders and synced web orders appear here for instant UPI QR, Cash, Card, or Split settlement.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(list, key = { it.id }) { order ->
                        BillingOrderCard(
                            order = order,
                            currencySymbol = currency,
                            onCollectPayment = { onCollectPayment(order) },
                            onViewReceipt = { onViewReceipt(order) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelSummary(label: String, amount: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun BillingOrderCard(
    order: CafeOrder,
    currencySymbol: String,
    onCollectPayment: () -> Unit,
    onViewReceipt: () -> Unit
) {
    val isPaid = order.parsedPaymentStatus == PaymentStatus.PAID

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("billing_card_${order.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = order.orderNumber,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    OrderStatusBadge(status = order.parsedStatus)
                }
                PaymentStatusBadge(order = order)
            }

            Text(
                text = "${order.tableOrCustomer} • ${order.totalItemCount} item${if (order.totalItemCount == 1L) "" else "s"} (${order.parsedOrderType.label})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatMoney(order.totalCents, currencySymbol),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isPaid && order.paymentReference.isNotBlank()) {
                        Text(
                            text = order.paymentReference,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isPaid) {
                    Button(
                        onClick = onCollectPayment,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("collect_payment_button_${order.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Process Payment")
                    }
                } else {
                    OutlinedButton(
                        onClick = onViewReceipt,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("view_receipt_button_${order.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receipt")
                    }
                }
            }
        }
    }
}
