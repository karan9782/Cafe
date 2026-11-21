package com.example.data.remote

import com.example.data.model.CafeOrder
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.google.firebase.Timestamp
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToLong

object PortalOrderMapper {

    fun buildPriceLookup(categories: List<AmpleMenuCategoryDto>): Map<String, Double> {
        if (categories.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, Double>()
        categories.forEach { cat ->
            cat.items.forEach { item ->
                map[item.id] = item.priceRupees
                map[item.name.lowercase()] = item.priceRupees
            }
        }
        return map
    }

    fun serializePriceLookup(priceLookup: Map<String, Double>): String {
        val obj = JSONObject()
        priceLookup.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun deserializePriceLookup(rawJson: String): Map<String, Double> {
        if (rawJson.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(rawJson)
            val map = mutableMapOf<String, Double>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.optDouble(key, 0.0)
            }
            map.toMap()
        }.getOrDefault(emptyMap())
    }

    fun mapAmpleOrderToCafeOrder(
        dto: AmpleOrderDto,
        userId: String,
        priceLookup: Map<String, Double> = emptyMap()
    ): CafeOrder {
        val safeDocId = "ample_" + dto.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifEmpty {
            dto.number
        }
        val mappedStatus = when (dto.status.lowercase()) {
            "new" -> OrderStatus.NEW
            "preparing" -> OrderStatus.PREPARING
            "ready" -> OrderStatus.READY
            "served", "completed" -> OrderStatus.COMPLETED
            else -> OrderStatus.NEW
        }
        val totalPaise = (dto.totalRupees * 100.0).roundToLong().coerceAtLeast(0L)
        val totalQty = dto.items.sumOf { it.qty }.coerceAtLeast(1L)

        val mappedItems = dto.items.map { item ->
            val resolvedRupees = item.priceRupees
                ?: priceLookup[item.id]
                ?: priceLookup[item.name.lowercase()]
            val unitPaise = if (resolvedRupees != null) {
                (resolvedRupees * 100.0).roundToLong()
            } else {
                (totalPaise / totalQty).coerceAtLeast(0L)
            }
            OrderLineItem(
                menuItemId = item.id.ifEmpty { item.name },
                name = item.name,
                quantity = item.qty,
                unitPriceCents = unitPaise,
                notes = ""
            )
        }

        val customerLabel = buildString {
            append("Table ${dto.table}")
            if (dto.name.isNotBlank()) {
                append(" • ${dto.name}")
            }
        }.take(60)

        val combinedNotes = buildList {
            if (dto.phone.isNotBlank()) add("Ph: ${dto.phone}")
            if (dto.note.isNotBlank()) add(dto.note)
        }.joinToString(" • ").take(300)

        return CafeOrder(
            id = safeDocId,
            userId = userId,
            orderNumber = "#${dto.number}",
            tableOrCustomer = customerLabel,
            orderType = OrderType.QR_SCAN.name,
            status = mappedStatus.name,
            paymentStatus = PaymentStatus.UNPAID.name,
            paymentMethod = PaymentMethod.NONE.name,
            items = mappedItems,
            subtotalCents = totalPaise,
            taxCents = 0L,
            tipCents = 0L,
            discountCents = 0L,
            totalCents = totalPaise,
            notes = combinedNotes,
            paymentReference = "",
            createdAt = Timestamp(Date(dto.createdAt)),
            updatedAt = Timestamp(Date(dto.createdAt))
        )
    }

    fun startOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
