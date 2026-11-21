package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.remote.AmpleBackendClient
import com.google.firebase.Timestamp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale
import kotlin.math.abs

fun formatMoney(cents: Long, currencySymbol: String = "₹"): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = abs(cents)
    val whole = absolute / 100
    val remainder = absolute % 100
    return if (currencySymbol == "₹" && remainder == 0L) {
        "$sign$currencySymbol$whole"
    } else {
        "$sign$currencySymbol$whole.${String.format(Locale.US, "%02d", remainder)}"
    }
}

fun formatElapsedDuration(createdAt: Timestamp?, nowMillis: Long): String {
    if (createdAt == null) return "Just now"
    val diffSeconds = ((nowMillis - createdAt.toDate().time) / 1000L).coerceAtLeast(0L)
    val minutes = diffSeconds / 60L
    val seconds = diffSeconds % 60L
    return when {
        minutes >= 60L -> "${minutes / 60L}h ${minutes % 60L}m"
        else -> String.format(Locale.US, "%02dm %02ds", minutes, seconds)
    }
}

fun buildUpiPaymentUri(
    upiId: String,
    cafeName: String,
    orderNumber: String,
    amountCents: Long,
    currencySymbol: String
): String {
    val cleanVpa = upiId.trim().ifEmpty { "cafekitchen@upi" }
    val amountStr = String.format(Locale.US, "%.2f", amountCents / 100.0)
    val currencyCode = if (currencySymbol == "₹" || currencySymbol == "$") "INR" else "INR"
    return Uri.Builder()
        .scheme("upi")
        .authority("pay")
        .appendQueryParameter("pa", cleanVpa)
        .appendQueryParameter("pn", cafeName.ifBlank { "Mawa's Cafe" })
        .appendQueryParameter("tn", "Order $orderNumber")
        .appendQueryParameter("am", amountStr)
        .appendQueryParameter("cu", currencyCode)
        .build()
        .toString()
}

fun extractTableNumber(tableCode: String): String {
    val digits = tableCode.filter { it.isDigit() }
    return digits.ifEmpty {
        tableCode.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").ifEmpty { "1" }
    }
}

fun buildTableOrderingUrl(baseWebsiteUrl: String, tableCode: String): String {
    val origin = AmpleBackendClient.extractOrigin(baseWebsiteUrl)
    val tableToken = extractTableNumber(tableCode)
    return "$origin/#table-$tableToken"
}

fun buildAppDeepLinkUri(tableCode: String): String {
    val tableToken = extractTableNumber(tableCode)
    return "cafekitchen://order?table=$tableToken"
}

fun extractTableCodeFromDeepLink(uri: Uri?): String? {
    if (uri == null) return null
    if (uri.scheme == "cafekitchen" && uri.host == "order") {
        val raw = uri.getQueryParameter("table")?.trim().orEmpty()
        if (raw.isNotEmpty()) return extractTableNumber(raw)
    }
    val fragment = uri.fragment?.trim().orEmpty()
    if (fragment.startsWith("table-", ignoreCase = true)) {
        val raw = fragment.removePrefix("table-").removePrefix("TABLE-").trim()
        if (raw.isNotEmpty()) return extractTableNumber(raw)
    }
    val queryTable = uri.getQueryParameter("table")?.trim().orEmpty()
    if (queryTable.isNotEmpty()) return extractTableNumber(queryTable)
    return null
}

fun generateQrImageBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

fun generateBarcodeImageBitmap(
    content: String,
    widthPx: Int = 640,
    heightPx: Int = 140
): ImageBitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 4)
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.CODE_128,
            widthPx,
            heightPx,
            hints
        )
        val pixels = IntArray(widthPx * heightPx)
        for (y in 0 until heightPx) {
            val offset = y * widthPx
            for (x in 0 until widthPx) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
