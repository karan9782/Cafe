package com.example.ui.qr

import android.content.Context
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CafeSettings
import com.example.data.model.PaymentMethod
import com.example.data.model.QrTableStation
import com.example.data.model.UiState
import com.example.data.remote.AmpleBackendClient
import com.example.data.remote.AmpleMenuCategoryDto
import com.example.data.remote.AmpleMenuItemDto
import com.example.ui.AmpleCartEntry
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.buildAppDeepLinkUri
import com.example.util.buildTableOrderingUrl
import com.example.util.buildUpiPaymentUri
import com.example.util.extractTableNumber
import com.example.util.formatMoney
import com.example.util.generateBarcodeImageBitmap
import com.example.util.generateQrImageBitmap
import kotlin.math.roundToLong

@Composable
fun QrOrderLinkScreen(
    qrTablesState: UiState<List<QrTableStation>>,
    portalMenuState: UiState<List<AmpleMenuCategoryDto>>,
    ampleCart: Map<String, AmpleCartEntry>,
    portalTableCount: Int,
    settings: CafeSettings,
    isSubmitting: Boolean,
    deepLinkTableCode: String?,
    onConsumeDeepLink: () -> Unit,
    onAddQrTable: (tableCode: String, label: String, customUrl: String?) -> Unit,
    onToggleQrTable: (QrTableStation) -> Unit,
    onDeleteQrTable: (QrTableStation) -> Unit,
    onSyncTablesAndMenu: () -> Unit,
    onAddAmpleItemToCart: (AmpleMenuItemDto) -> Unit,
    onDecrementAmpleItemFromCart: (String) -> Unit,
    onClearAmpleCart: () -> Unit,
    onOpenUrlInPortal: (String) -> Unit,
    onPlaceAmpleQrOrder: (
        tableCode: String,
        customerName: String,
        customerPhone: String,
        notes: String,
        adultConfirmed: Boolean,
        immediatePayMethod: PaymentMethod?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddTableDialog by remember { mutableStateOf(false) }
    var activePreviewTable by remember { mutableStateOf<QrTableStation?>(null) }

    LaunchedEffect(deepLinkTableCode, qrTablesState) {
        val code = deepLinkTableCode?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val existingTables = (qrTablesState as? UiState.Success)?.data.orEmpty()
        val normalizedNum = extractTableNumber(code)
        val matched = existingTables.firstOrNull {
            extractTableNumber(it.tableCode).equals(normalizedNum, ignoreCase = true)
        } ?: QrTableStation(
            id = "table_$normalizedNum",
            tableCode = "T$normalizedNum",
            label = "Table $normalizedNum",
            linkedUrl = buildTableOrderingUrl(settings.websiteUrl, normalizedNum),
            active = true
        )
        activePreviewTable = matched
        onConsumeDeepLink()
    }

    val origin = remember(settings.websiteUrl) {
        AmpleBackendClient.extractOrigin(settings.websiteUrl)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Owner QR & Barcode Linking Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic Table QR & Barcodes ($portalTableCount Tables)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Linked to $origin/#table-<N> & live menu in ₹.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$origin/#table-3",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncTablesAndMenu,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("sync_ample_tables_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync from Portal")
                        }
                        OutlinedButton(
                            onClick = { showAddTableDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_qr_table_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Table")
                        }
                    }
                }
            }
        }

        when (qrTablesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
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
                            text = qrTablesState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val tables = qrTablesState.data
                if (tables.isEmpty()) {
                    item {
                        EmptyQrTablesCard(
                            isSubmitting = isSubmitting,
                            onSyncTables = onSyncTablesAndMenu,
                            onAddCustomTable = { showAddTableDialog = true }
                        )
                    }
                } else {
                    items(tables, key = { it.id }) { table ->
                        QrTableStationCard(
                            table = table,
                            cafeName = settings.cafeName,
                            onToggleActive = { onToggleQrTable(table) },
                            onDelete = { onDeleteQrTable(table) },
                            onShareStandee = {
                                shareTableStandee(context, settings.cafeName, table)
                            },
                            onOpenTableWebPage = {
                                onOpenUrlInPortal(table.linkedUrl)
                            },
                            onPreviewCustomerOrder = {
                                activePreviewTable = table
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddTableDialog) {
        AddQrTableDialog(
            baseWebsiteUrl = settings.websiteUrl,
            isSubmitting = isSubmitting,
            onDismiss = { showAddTableDialog = false },
            onSave = { code, label, customUrl ->
                onAddQrTable(code, label, customUrl)
                showAddTableDialog = false
            }
        )
    }

    val previewTable = activePreviewTable
    if (previewTable != null) {
        CustomerQrOrderPreviewSheet(
            table = previewTable,
            portalMenuState = portalMenuState,
            ampleCart = ampleCart,
            settings = settings,
            isSubmitting = isSubmitting,
            onDismiss = { activePreviewTable = null },
            onRefreshMenu = onSyncTablesAndMenu,
            onOpenTableInPortal = {
                activePreviewTable = null
                onOpenUrlInPortal(previewTable.linkedUrl)
            },
            onAddToCart = onAddAmpleItemToCart,
            onDecrementFromCart = onDecrementAmpleItemFromCart,
            onClearCart = onClearAmpleCart,
            onSubmitQrOrder = { customerName, customerPhone, notes, adultConfirmed, payMethod ->
                onPlaceAmpleQrOrder(
                    previewTable.tableCode,
                    customerName,
                    customerPhone,
                    notes,
                    adultConfirmed,
                    payMethod
                )
                activePreviewTable = null
            }
        )
    }
}

@Composable
private fun QrTableStationCard(
    table: QrTableStation,
    cafeName: String,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onShareStandee: () -> Unit,
    onOpenTableWebPage: () -> Unit,
    onPreviewCustomerOrder: () -> Unit
) {
    val qrBitmap = remember(table.linkedUrl) {
        generateQrImageBitmap(table.linkedUrl, sizePx = 360)
    }
    val barcodeCode = remember(table.tableCode) {
        "CAFE-${table.tableCode.uppercase()}"
    }
    val barcodeBitmap = remember(barcodeCode) {
        generateBarcodeImageBitmap(barcodeCode, widthPx = 560, heightPx = 120)
    }
    val deepLink = remember(table.tableCode) {
        buildAppDeepLinkUri(table.tableCode)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qr_table_card_${table.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = table.tableCode,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Column {
                        Text(
                            text = table.label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (table.active) "Live Portal QR" else "Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (table.active) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Switch(
                    checked = table.active,
                    onCheckedChange = { onToggleActive() }
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove ${table.label}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Scannable QR & Code-128 Barcode Preview Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR code for ${table.label}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(116.dp)
                                .padding(8.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$cafeName • Barcode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (barcodeBitmap != null) {
                                Image(
                                    bitmap = barcodeBitmap,
                                    contentDescription = "Barcode for ${table.tableCode}",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                )
                            }
                            Text(
                                text = barcodeCode,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = Color.Black
                            )
                        }
                    }
                    Text(
                        text = deepLink,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = table.linkedUrl,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenTableWebPage) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open ${table.label} in portal",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareStandee,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_qr_table_${table.id}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share QR")
                }
                Button(
                    onClick = onPreviewCustomerOrder,
                    enabled = table.active,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("preview_qr_order_${table.id}")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Order on Table")
                }
            }
        }
    }
}

@Composable
private fun EmptyQrTablesCard(
    isSubmitting: Boolean,
    onSyncTables: () -> Unit,
    onAddCustomTable: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Sync Tables from ample.run",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Pull all table stations and live menu items from your ample.run portal to generate dynamic #table-N QR codes & barcodes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onSyncTables,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Tables & Menu from ample.run")
            }
            OutlinedButton(
                onClick = onAddCustomTable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Custom Table")
            }
        }
    }
}

@Composable
private fun AddQrTableDialog(
    baseWebsiteUrl: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (tableCode: String, label: String, customUrl: String?) -> Unit
) {
    var tableCode by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewUrl = remember(tableCode, customUrl, baseWebsiteUrl) {
        customUrl.trim().ifEmpty {
            buildTableOrderingUrl(baseWebsiteUrl, tableCode.ifBlank { "T16" })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Table QR & Barcode") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = tableCode,
                    onValueChange = {
                        tableCode = it.uppercase().take(16)
                        errorMsg = null
                    },
                    label = { Text("Table / Barcode Code") },
                    placeholder = { Text("T16") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it.take(40)
                        errorMsg = null
                    },
                    label = { Text("Display Label") },
                    placeholder = { Text("Table 16") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it.take(300) },
                    label = { Text("Linked Ordering URL (Optional)") },
                    placeholder = { Text("Auto-links to #table-<N>") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Preview Link: $previewUrl",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val cleanCode = tableCode.trim()
                    val cleanLabel = label.trim()
                    when {
                        cleanCode.isEmpty() -> errorMsg = "Enter a table code (e.g. T16)."
                        cleanLabel.isEmpty() -> errorMsg = "Enter a display label (e.g. Table 16)."
                        else -> onSave(cleanCode, cleanLabel, customUrl.trim().ifEmpty { null })
                    }
                }
            ) {
                Text("Generate QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomerQrOrderPreviewSheet(
    table: QrTableStation,
    portalMenuState: UiState<List<AmpleMenuCategoryDto>>,
    ampleCart: Map<String, AmpleCartEntry>,
    settings: CafeSettings,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onRefreshMenu: () -> Unit,
    onOpenTableInPortal: () -> Unit,
    onAddToCart: (AmpleMenuItemDto) -> Unit,
    onDecrementFromCart: (String) -> Unit,
    onClearCart: () -> Unit,
    onSubmitQrOrder: (
        customerName: String,
        customerPhone: String,
        notes: String,
        adultConfirmed: Boolean,
        immediatePayMethod: PaymentMethod?
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var kitchenNotes by remember { mutableStateOf("") }
    var adultConfirmed by remember { mutableStateOf(false) }
    var payNowWithUpi by remember { mutableStateOf(false) }

    val cartEntries = ampleCart.values.toList()
    val hasAdultItems = cartEntries.any { it.item.adultOnly }
    val subtotalCents = cartEntries.sumOf {
        (it.item.priceRupees * 100.0).roundToLong() * it.quantity
    }
    val totalCents = subtotalCents

    val upiUri = remember(totalCents, settings.upiId, settings.cafeName, table.tableCode) {
        buildUpiPaymentUri(
            upiId = settings.upiId,
            cafeName = settings.cafeName,
            orderNumber = "Table-${extractTableNumber(table.tableCode)}",
            amountCents = totalCents,
            currencySymbol = settings.currencySymbol
        )
    }
    val upiQrBitmap = remember(upiUri, payNowWithUpi, totalCents) {
        if (payNowWithUpi && totalCents > 0L) generateQrImageBitmap(upiUri, sizePx = 280) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${table.label} • Live Portal Menu",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = table.linkedUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (cartEntries.isNotEmpty()) {
                    TextButton(onClick = onClearCart) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenTableInPortal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open ${table.label} Live Web Page")
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search coffee, chai, maggie, pizza...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            when (portalMenuState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = portalMenuState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = onRefreshMenu) {
                                Text("Retry Loading Portal Menu")
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val categories = portalMenuState.data
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryName == null,
                            onClick = { selectedCategoryName = null },
                            label = { Text("All") }
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryName == cat.name,
                                onClick = { selectedCategoryName = cat.name },
                                label = { Text(cat.name) }
                            )
                        }
                    }

                    val query = searchQuery.trim()
                    categories
                        .filter { selectedCategoryName == null || it.name == selectedCategoryName }
                        .forEach { category ->
                            val matchingItems = category.items.filter { item ->
                                query.isEmpty() ||
                                    item.name.contains(query, ignoreCase = true) ||
                                    category.name.contains(query, ignoreCase = true)
                            }
                            if (matchingItems.isNotEmpty()) {
                                Text(
                                    text = "${category.name} (${matchingItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                matchingItems.forEach { item ->
                                    val qty = ampleCart[item.id]?.quantity ?: 0L
                                    val itemPaise = (item.priceRupees * 100.0).roundToLong()
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            width = if (qty > 0L) 1.5.dp else 1.dp,
                                            color = if (qty > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = formatMoney(itemPaise, settings.currencySymbol),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (qty > 0L) {
                                                IconButton(onClick = { onDecrementFromCart(item.id) }) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Remove ${item.name}")
                                                }
                                                Text(
                                                    text = "$qty",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                                IconButton(onClick = { onAddToCart(item) }) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add ${item.name}")
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { onAddToCart(item) },
                                                    modifier = Modifier.height(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            }

            if (cartEntries.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it.take(40) },
                    label = { Text("Guest Name") },
                    placeholder = { Text("Aarav") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it.filter { ch -> ch.isDigit() }.take(10) },
                    label = { Text("10-Digit Mobile (Required by Portal)") },
                    placeholder = { Text("9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kitchenNotes,
                    onValueChange = { kitchenNotes = it.take(120) },
                    label = { Text("Kitchen Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasAdultItems) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = adultConfirmed,
                            onCheckedChange = { adultConfirmed = it }
                        )
                        Text(
                            text = "Confirm guest is 18 or older (required for cigarettes)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "Payment Option",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !payNowWithUpi,
                        onClick = { payNowWithUpi = false },
                        label = { Text("Pay at Counter") }
                    )
                    FilterChip(
                        selected = payNowWithUpi,
                        onClick = { payNowWithUpi = true },
                        label = { Text("Pay Now via UPI QR") }
                    )
                }

                if (payNowWithUpi && upiQrBitmap != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                                Image(
                                    bitmap = upiQrBitmap,
                                    contentDescription = "Customer UPI payment QR",
                                    modifier = Modifier
                                        .size(92.dp)
                                        .padding(6.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Scan to Pay ${formatMoney(totalCents, settings.currencySymbol)}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)))
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open UPI App")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Payable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        formatMoney(totalCents, settings.currencySymbol),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        onSubmitQrOrder(
                            customerName,
                            customerPhone,
                            kitchenNotes,
                            adultConfirmed,
                            if (payNowWithUpi) PaymentMethod.UPI else null
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("place_qr_order_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Place Portal Order • ${formatMoney(totalCents, settings.currencySymbol)}")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun shareTableStandee(
    context: Context,
    cafeName: String,
    table: QrTableStation
) {
    val barcodeCode = "CAFE-${table.tableCode.uppercase()}"
    val deepLink = buildAppDeepLinkUri(table.tableCode)
    val message = buildString {
        appendLine("$cafeName — ${table.label} (${table.tableCode})")
        appendLine("Scan QR or Barcode [$barcodeCode] to order directly:")
        appendLine("Web Menu: ${table.linkedUrl}")
        appendLine("App Link: $deepLink")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$cafeName QR Standee - ${table.label}")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Share Table QR Standee"))
}
