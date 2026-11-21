package com.example.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AmpleMenuItemDto(
    val id: String,
    val name: String,
    val priceRupees: Double,
    val categoryName: String,
    val adultOnly: Boolean
)

data class AmpleMenuCategoryDto(
    val name: String,
    val adult: Boolean,
    val note: String,
    val items: List<AmpleMenuItemDto>
)

data class AmpleMenuPayload(
    val categories: List<AmpleMenuCategoryDto>,
    val tables: Int
)

data class AmpleOrderItemDto(
    val id: String,
    val name: String,
    val qty: Long,
    val priceRupees: Double?
)

data class AmpleOrderDto(
    val id: String,
    val number: String,
    val table: String,
    val status: String,
    val createdAt: Long,
    val name: String,
    val phone: String,
    val note: String,
    val items: List<AmpleOrderItemDto>,
    val totalRupees: Double
)

class AmpleApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

class AmpleBackendClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchMenuAndTables(portalUrl: String): Result<AmpleMenuPayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val origin = extractOrigin(portalUrl)
                val request = Request.Builder()
                    .url("$origin/api/menu")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw AmpleApiException(
                            response.code,
                            parseErrorMessage(body, "Failed to load menu (${response.code})")
                        )
                    }
                    val root = JSONObject(body)
                    val menuObj = root.optJSONObject("menu") ?: JSONObject()
                    val catArr = menuObj.optJSONArray("categories") ?: JSONArray()
                    val categories = mutableListOf<AmpleMenuCategoryDto>()
                    for (i in 0 until catArr.length()) {
                        val catObj = catArr.optJSONObject(i) ?: continue
                        val catName = catObj.optString("name").trim()
                        if (catName.isEmpty()) continue
                        val isAdult = catObj.optBoolean("adult", false)
                        val note = catObj.optString("note", "").trim()
                        val itemsArr = catObj.optJSONArray("items") ?: JSONArray()
                        val items = mutableListOf<AmpleMenuItemDto>()
                        for (j in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.optJSONObject(j) ?: continue
                            val itemId = itemObj.optString("id").trim()
                            val itemName = itemObj.optString("name").trim()
                            val price = itemObj.optDouble("price", 0.0)
                            if (itemId.isNotEmpty() && itemName.isNotEmpty()) {
                                items.add(
                                    AmpleMenuItemDto(
                                        id = itemId,
                                        name = itemName,
                                        priceRupees = price.coerceAtLeast(0.0),
                                        categoryName = catName,
                                        adultOnly = isAdult
                                    )
                                )
                            }
                        }
                        if (items.isNotEmpty()) {
                            categories.add(
                                AmpleMenuCategoryDto(
                                    name = catName,
                                    adult = isAdult,
                                    note = note,
                                    items = items
                                )
                            )
                        }
                    }
                    val tables = root.optInt("tables", 15).coerceIn(1, 50)
                    AmpleMenuPayload(categories = categories, tables = tables)
                }
            }
        }

    suspend fun verifyStaffKey(portalUrl: String, staffKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanKey = staffKey.trim()
                require(cleanKey.isNotEmpty()) { "Enter your portal staff key." }
                val origin = extractOrigin(portalUrl)
                val request = Request.Builder()
                    .url("$origin/api/auth")
                    .header("x-staff-key", cleanKey)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw AmpleApiException(
                            response.code,
                            parseErrorMessage(body, "Invalid staff key (${response.code})")
                        )
                    }
                }
            }
        }

    suspend fun fetchKitchenOrders(
        portalUrl: String,
        staffKey: String,
        sinceMillis: Long
    ): Result<List<AmpleOrderDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = staffKey.trim()
            require(cleanKey.isNotEmpty()) { "Staff key required." }
            val origin = extractOrigin(portalUrl)
            val request = Request.Builder()
                .url("$origin/api/kitchen?since=$sinceMillis")
                .header("x-staff-key", cleanKey)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AmpleApiException(
                        response.code,
                        parseErrorMessage(body, "Kitchen sync failed (${response.code})")
                    )
                }
                val trimmed = body.trim()
                val arr = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    JSONObject(trimmed).optJSONArray("orders") ?: JSONArray()
                }
                parseOrderArray(arr)
            }
        }
    }

    suspend fun updateOrderStatus(
        portalUrl: String,
        staffKey: String,
        ampleOrderId: String,
        ampleStatus: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = staffKey.trim()
            require(cleanKey.isNotEmpty()) { "Staff key required." }
            val origin = extractOrigin(portalUrl)
            val payload = JSONObject().put("status", ampleStatus).toString()
            val request = Request.Builder()
                .url("$origin/api/orders/$ampleOrderId")
                .header("x-staff-key", cleanKey)
                .patch(payload.toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AmpleApiException(
                        response.code,
                        parseErrorMessage(body, "Could not update portal order (${response.code})")
                    )
                }
            }
        }
    }

    suspend fun placeCustomerOrder(
        portalUrl: String,
        tableNumber: String,
        items: List<Pair<String, Long>>,
        note: String,
        customerName: String,
        customerPhone: String,
        adultConfirmed: Boolean
    ): Result<AmpleOrderDto> = withContext(Dispatchers.IO) {
        runCatching {
            val origin = extractOrigin(portalUrl)
            val itemsArray = JSONArray()
            items.forEach { (id, qty) ->
                itemsArray.put(JSONObject().put("id", id).put("qty", qty))
            }
            val bodyJson = JSONObject()
                .put("table", tableNumber)
                .put("items", itemsArray)
                .put("note", note.trim().take(120))
                .put("adult", adultConfirmed)
                .put("name", customerName.trim().take(40))
                .put("phone", customerPhone.trim())

            val request = Request.Builder()
                .url("$origin/api/orders")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AmpleApiException(
                        response.code,
                        parseErrorMessage(body, "Failed to place order on portal (${response.code})")
                    )
                }
                parseOrderObject(JSONObject(body))
            }
        }
    }

    private fun parseOrderArray(arr: JSONArray): List<AmpleOrderDto> {
        val list = mutableListOf<AmpleOrderDto>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(parseOrderObject(obj))
        }
        return list
    }

    private fun parseOrderObject(obj: JSONObject): AmpleOrderDto {
        val itemsArr = obj.optJSONArray("items") ?: JSONArray()
        val parsedItems = mutableListOf<AmpleOrderItemDto>()
        for (j in 0 until itemsArr.length()) {
            val itemObj = itemsArr.optJSONObject(j) ?: continue
            val price = if (itemObj.has("price") && !itemObj.isNull("price")) {
                itemObj.optDouble("price")
            } else {
                null
            }
            parsedItems.add(
                AmpleOrderItemDto(
                    id = itemObj.optString("id", "").trim(),
                    name = itemObj.optString("name", "Item").trim(),
                    qty = itemObj.optLong("qty", 1L).coerceAtLeast(1L),
                    priceRupees = price
                )
            )
        }
        val rawNumber = obj.opt("number")?.toString()?.trim().orEmpty()
        val rawTable = obj.opt("table")?.toString()?.trim().orEmpty()
        return AmpleOrderDto(
            id = obj.optString("id", "").trim(),
            number = rawNumber.ifEmpty { "0" },
            table = rawTable.ifEmpty { "1" },
            status = obj.optString("status", "new").trim().lowercase(),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            name = obj.optString("name", "").trim(),
            phone = obj.optString("phone", "").trim(),
            note = obj.optString("note", "").trim(),
            items = parsedItems,
            totalRupees = obj.optDouble("total", 0.0).coerceAtLeast(0.0)
        )
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("error").takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    companion object {
        const val DEFAULT_ORIGIN = "https://gdp4fzx3f5u6.ample.run"

        fun extractOrigin(portalUrl: String): String {
            val trimmed = portalUrl.trim()
            if (trimmed.isEmpty()) return DEFAULT_ORIGIN
            return try {
                val uri = Uri.parse(trimmed)
                val scheme = uri.scheme?.lowercase()
                val host = uri.host
                if ((scheme == "https" || scheme == "http") && !host.isNullOrBlank()) {
                    val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) {
                        ":${uri.port}"
                    } else {
                        ""
                    }
                    "$scheme://$host$portPart"
                } else {
                    DEFAULT_ORIGIN
                }
            } catch (_: Exception) {
                DEFAULT_ORIGIN
            }
        }
    }
}
