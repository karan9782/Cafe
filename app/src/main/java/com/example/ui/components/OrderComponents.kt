package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CafeOrder
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.formatMoney

@Composable
fun OrderStatusBadge(status: OrderStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        OrderStatus.NEW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        OrderStatus.PREPARING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        OrderStatus.READY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        OrderStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.error
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50.dp),
        modifier = modifier
    ) {
        Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PaymentStatusBadge(
    order: CafeOrder,
    modifier: Modifier = Modifier
) {
    val isPaid = order.parsedPaymentStatus == PaymentStatus.PAID
    val bg = if (isPaid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isPaid) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val label = if (isPaid) "PAID • ${order.parsedPaymentMethod.label}" else "UNPAID"

    Surface(
        color = bg,
        shape = RoundedCornerShape(50.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun IncomingOrderAlertBanner(
    alertOrder: CafeOrder?,
    currencySymbol: String,
    onStartPrep: (CafeOrder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alertOrder != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        if (alertOrder != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("incoming_order_alert_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "New order alert",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Order ${alertOrder.orderNumber} • ${alertOrder.tableOrCustomer}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${alertOrder.totalItemCount} items • ${formatMoney(alertOrder.totalCents, currencySymbol)} (${alertOrder.parsedOrderType.label})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss alert",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Dismiss",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onStartPrep(alertOrder) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("alert_start_prep_button")
                        ) {
                            Text("Start Preparing")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DigitalReceiptDialog(
    order: CafeOrder,
    cafeName: String,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (order.parsedPaymentStatus == PaymentStatus.PAID) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.ReceiptLong
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = cafeName,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Receipt ${order.orderNumber} • ${order.tableOrCustomer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatMoney(item.unitPriceCents * item.quantity, currencySymbol),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReceiptLine("Subtotal", formatMoney(order.subtotalCents, currencySymbol))
                if (order.discountCents > 0L) {
                    ReceiptLine("Discount", "-${formatMoney(order.discountCents, currencySymbol)}")
                }
                ReceiptLine("Tax", formatMoney(order.taxCents, currencySymbol))
                if (order.tipCents > 0L) {
                    ReceiptLine("Tip", formatMoney(order.tipCents, currencySymbol))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total (${order.parsedPaymentMethod.label})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMoney(order.totalCents, currencySymbol),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (order.paymentReference.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ref: ${order.paymentReference}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { shareReceiptText(context, cafeName, order, currencySymbol) },
                modifier = Modifier.testTag("share_receipt_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Receipt")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareReceiptText(
    context: Context,
    cafeName: String,
    order: CafeOrder,
    currencySymbol: String
) {
    val lines = buildString {
        appendLine("=== $cafeName ===")
        appendLine("Order ${order.orderNumber} • ${order.tableOrCustomer}")
        appendLine("Channel: ${order.parsedOrderType.label}")
        appendLine("---------------------------")
        order.items.forEach {
            appendLine("${it.quantity}x ${it.name}  ${formatMoney(it.unitPriceCents * it.quantity, currencySymbol)}")
        }
        appendLine("---------------------------")
        appendLine("Subtotal: ${formatMoney(order.subtotalCents, currencySymbol)}")
        if (order.discountCents > 0L) appendLine("Discount: -${formatMoney(order.discountCents, currencySymbol)}")
        appendLine("Tax: ${formatMoney(order.taxCents, currencySymbol)}")
        if (order.tipCents > 0L) appendLine("Tip: ${formatMoney(order.tipCents, currencySymbol)}")
        appendLine("TOTAL: ${formatMoney(order.totalCents, currencySymbol)}")
        appendLine("Status: ${order.parsedPaymentStatus.label} (${order.parsedPaymentMethod.label})")
        if (order.paymentReference.isNotBlank()) appendLine("Ref: ${order.paymentReference}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$cafeName Receipt ${order.orderNumber}")
        putExtra(Intent.EXTRA_TEXT, lines)
    }
    context.startActivity(Intent.createChooser(intent, "Share Customer Receipt"))
}
