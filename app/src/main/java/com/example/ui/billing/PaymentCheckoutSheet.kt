package com.example.ui.billing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CafeOrder
import com.example.data.model.CafeSettings
import com.example.data.model.PaymentMethod
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.buildUpiPaymentUri
import com.example.util.formatMoney
import com.example.util.generateQrImageBitmap
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaymentCheckoutSheet(
    order: CafeOrder,
    settings: CafeSettings,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmPayment: (
        method: PaymentMethod,
        tipCents: Long,
        discountCents: Long,
        paymentReference: String,
        markCompleted: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currency = settings.currencySymbol

    var selectedMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var tipPercent by remember { mutableIntStateOf(0) }
    var discountPercent by remember { mutableIntStateOf(0) }
    var splitCount by remember { mutableIntStateOf(1) }
    var cashTenderedCents by remember { mutableLongStateOf(0L) }
    var referenceText by remember { mutableStateOf("") }
    var markCompleted by remember { mutableStateOf(true) }

    val discountCents = ((order.subtotalCents * discountPercent) / 100.0).roundToLong()
    val afterDiscount = (order.subtotalCents - discountCents).coerceAtLeast(0L)
    val tipCents = ((afterDiscount * tipPercent) / 100.0).roundToLong()
    val finalTotalCents = (afterDiscount + order.taxCents + tipCents).coerceAtLeast(0L)
    val perGuestCents = if (splitCount > 1) (finalTotalCents + splitCount - 1) / splitCount else finalTotalCents

    val effectiveCashTendered = if (cashTenderedCents <= 0L) finalTotalCents else cashTenderedCents
    val changeDueCents = (effectiveCashTendered - finalTotalCents).coerceAtLeast(0L)

    val upiUri = remember(settings.upiId, settings.cafeName, order.orderNumber, finalTotalCents, currency) {
        buildUpiPaymentUri(
            upiId = settings.upiId,
            cafeName = settings.cafeName,
            orderNumber = order.orderNumber,
            amountCents = finalTotalCents,
            currencySymbol = currency
        )
    }
    val qrBitmap = remember(upiUri) {
        generateQrImageBitmap(upiUri, sizePx = 420)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settle Bill ${order.orderNumber}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${order.tableOrCustomer} • ${order.totalItemCount} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = formatMoney(finalTotalCents, currency),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tip (Optional)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 5, 10, 15).forEach { pct ->
                        FilterChip(
                            selected = tipPercent == pct,
                            onClick = { tipPercent = pct },
                            label = { Text(if (pct == 0) "No Tip" else "$pct%") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Discount", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 10).forEach { pct ->
                            FilterChip(
                                selected = discountPercent == pct,
                                onClick = { discountPercent = pct },
                                label = { Text(if (pct == 0) "None" else "$pct%") }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Split Bill", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 4).forEach { ways ->
                            FilterChip(
                                selected = splitCount == ways,
                                onClick = { splitCount = ways },
                                label = { Text("${ways}x") }
                            )
                        }
                    }
                }
            }

            if (splitCount > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Split $splitCount ways",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = "${formatMoney(perGuestCents, currency)} / guest",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(text = "Payment Method", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMethodOption(
                    label = "UPI / QR",
                    icon = Icons.Default.QrCode2,
                    selected = selectedMethod == PaymentMethod.UPI,
                    onClick = { selectedMethod = PaymentMethod.UPI },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pay_method_upi")
                )
                PaymentMethodOption(
                    label = "Cash",
                    icon = Icons.Default.Payments,
                    selected = selectedMethod == PaymentMethod.CASH,
                    onClick = { selectedMethod = PaymentMethod.CASH },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pay_method_cash")
                )
                PaymentMethodOption(
                    label = "Card",
                    icon = Icons.Default.CreditCard,
                    selected = selectedMethod == PaymentMethod.CARD,
                    onClick = { selectedMethod = PaymentMethod.CARD },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pay_method_card")
                )
                PaymentMethodOption(
                    label = "Split",
                    icon = Icons.Default.AccountBalanceWallet,
                    selected = selectedMethod == PaymentMethod.SPLIT,
                    onClick = { selectedMethod = PaymentMethod.SPLIT },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pay_method_split")
                )
            }

            when (selectedMethod) {
                PaymentMethod.UPI -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Customer Scan-to-Pay UPI QR",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .background(Color.White, RoundedCornerShape(14.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "UPI payment QR code for ${formatMoney(finalTotalCents, currency)}",
                                        modifier = Modifier.size(160.dp)
                                    )
                                }
                            }
                            Text(
                                text = "UPI ID: ${settings.upiId.ifBlank { "cafekitchen@upi (set in Portal > Settings)" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                    try {
                                        context.startActivity(upiIntent)
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open UPI Payment App")
                            }
                        }
                    }
                }

                PaymentMethod.CASH -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Rupee Cash Tender & Change Calculator",
                                style = MaterialTheme.typography.titleSmall
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = cashTenderedCents == 0L || cashTenderedCents == finalTotalCents,
                                    onClick = { cashTenderedCents = finalTotalCents },
                                    label = { Text("Exact") }
                                )
                                listOf(10000L, 20000L, 50000L, 100000L, 200000L).forEach { note ->
                                    if (note >= finalTotalCents) {
                                        FilterChip(
                                            selected = cashTenderedCents == note,
                                            onClick = { cashTenderedCents = note },
                                            label = { Text(formatMoney(note, currency)) }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Received: ${formatMoney(effectiveCashTendered, currency)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Change: ${formatMoney(changeDueCents, currency)}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                PaymentMethod.CARD, PaymentMethod.SPLIT, PaymentMethod.NONE -> {
                    OutlinedTextField(
                        value = referenceText,
                        onValueChange = { referenceText = it.take(80) },
                        label = { Text("Card Last 4 / Reference (Optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mark order Completed",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Move order to completed history once paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = markCompleted,
                    onCheckedChange = { markCompleted = it }
                )
            }

            Button(
                onClick = {
                    val computedRef = when {
                        referenceText.isNotBlank() -> referenceText.trim()
                        selectedMethod == PaymentMethod.CASH ->
                            "Cash ${formatMoney(effectiveCashTendered, currency)} (Change ${formatMoney(changeDueCents, currency)})"
                        selectedMethod == PaymentMethod.UPI ->
                            "UPI ${settings.upiId.ifBlank { "QR" }}"
                        selectedMethod == PaymentMethod.SPLIT ->
                            "Split ${splitCount}x (${formatMoney(perGuestCents, currency)}/guest)"
                        else -> "Card Payment Settled"
                    }
                    onConfirmPayment(
                        selectedMethod,
                        tipCents,
                        discountCents,
                        computedRef,
                        markCompleted
                    )
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_payment_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onSecondary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm ${formatMoney(finalTotalCents, currency)} via ${selectedMethod.label}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PaymentMethodOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(66.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
