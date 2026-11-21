package com.example.data

import android.content.Context
import com.example.R
import com.example.data.model.CafeOrder
import com.example.data.model.CafeSettings
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.OrderLineItem
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentStatus
import com.example.data.model.QrTableStation
import com.example.util.buildTableOrderingUrl
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

class CafeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeOrders(userId: String): Flow<List<CafeOrder>> = flow {
        val path = "users/$userId/orders"
        emitAll(
            db.collection("users").document(userId).collection("orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CafeOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMenuItems(userId: String): Flow<List<MenuItem>> = flow {
        val path = "users/$userId/menu_items"
        emitAll(
            db.collection("users").document(userId).collection("menu_items")
                .orderBy("name", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MenuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeQrTables(userId: String): Flow<List<QrTableStation>> = flow {
        val path = "users/$userId/qr_tables"
        emitAll(
            db.collection("users").document(userId).collection("qr_tables")
                .orderBy("tableCode", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        QrTableStation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSettings(userId: String): Flow<CafeSettings?> = flow {
        val path = "users/$userId/settings/profile"
        emitAll(
            db.collection("users").document(userId).collection("settings").document("profile")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CafeSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDefaultSettings(
        userId: String,
        defaultWebsiteUrl: String
    ): Result<Unit> {
        val path = "users/$userId/settings/profile"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
                .collection("settings").document("profile")
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "cafeName" to "Mawa’s Cafe",
                    "websiteUrl" to defaultWebsiteUrl.take(300),
                    "upiId" to "",
                    "currencySymbol" to "₹",
                    "taxRatePercent" to 0.0,
                    "alertsEnabled" to true,
                    "soundAlertsEnabled" to true,
                    "nextOrderCounter" to 101L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else if (existing.getString("currencySymbol") == "$" &&
                (existing.getLong("nextOrderCounter") ?: 101L) == 101L
            ) {
                docRef.update(
                    mapOf(
                        "cafeName" to "Mawa’s Cafe",
                        "currencySymbol" to "₹",
                        "taxRatePercent" to 0.0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        userId: String,
        cafeName: String,
        websiteUrl: String,
        upiId: String,
        currencySymbol: String,
        taxRatePercent: Double,
        alertsEnabled: Boolean,
        soundAlertsEnabled: Boolean
    ): Result<Unit> {
        val path = "users/$userId/settings/profile"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanName = cafeName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Cafe name is required." }
            require(currencySymbol in setOf("₹", "$", "€", "£")) { "Unsupported currency." }
            val clampedTax = taxRatePercent.coerceIn(0.0, 30.0)

            val docRef = db.collection("users").document(uid)
                .collection("settings").document("profile")
            docRef.update(
                mapOf(
                    "cafeName" to cleanName,
                    "websiteUrl" to websiteUrl.trim().take(300),
                    "upiId" to upiId.trim().take(100),
                    "currencySymbol" to currencySymbol,
                    "taxRatePercent" to clampedTax,
                    "alertsEnabled" to alertsEnabled,
                    "soundAlertsEnabled" to soundAlertsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createOrder(
        userId: String,
        orderNumberNumber: Long,
        tableOrCustomer: String,
        orderType: OrderType,
        items: List<OrderLineItem>,
        taxRatePercent: Double,
        notes: String,
        immediatePaymentMethod: PaymentMethod? = null,
        immediateTipCents: Long = 0L,
        immediateDiscountCents: Long = 0L,
        immediatePaymentReference: String = ""
    ): Result<String> {
        var docPath = "users/$userId/orders"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanCustomer = tableOrCustomer.trim().take(60)
            require(cleanCustomer.isNotEmpty()) { "Table or customer label is required." }
            require(items.isNotEmpty() && items.size <= 25) { "Order must contain 1 to 25 items." }

            val subtotal = items.sumOf { it.unitPriceCents * it.quantity }.coerceIn(0L, 10_000_000L)
            val discount = immediateDiscountCents.coerceIn(0L, subtotal)
            val taxableBase = (subtotal - discount).coerceAtLeast(0L)
            val tax = (taxableBase * (taxRatePercent.coerceIn(0.0, 30.0) / 100.0))
                .roundToLong()
                .coerceIn(0L, 10_000_000L)
            val tip = immediateTipCents.coerceIn(0L, 10_000_000L)
            val total = (taxableBase + tax + tip).coerceIn(0L, 10_000_000L)

            val isPaidNow = immediatePaymentMethod != null && immediatePaymentMethod != PaymentMethod.NONE
            val orderDocRef = db.collection("users").document(uid).collection("orders").document()
            docPath = orderDocRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "orderNumber" to "#${orderNumberNumber.coerceIn(1L, 999999L)}",
                "tableOrCustomer" to cleanCustomer,
                "orderType" to orderType.name,
                "status" to OrderStatus.NEW.name,
                "paymentStatus" to if (isPaidNow) PaymentStatus.PAID.name else PaymentStatus.UNPAID.name,
                "paymentMethod" to (immediatePaymentMethod?.name ?: PaymentMethod.NONE.name),
                "items" to items.map { it.toFirestoreMap() },
                "subtotalCents" to subtotal,
                "taxCents" to tax,
                "tipCents" to tip,
                "discountCents" to discount,
                "totalCents" to total,
                "notes" to notes.trim().take(300),
                "paymentReference" to immediatePaymentReference.trim().take(100),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isPaidNow) {
                payload["paidAt"] = FieldValue.serverTimestamp()
            }

            val settingsRef = db.collection("users").document(uid)
                .collection("settings").document("profile")
            val batch = db.batch()
            batch.set(orderDocRef, payload)
            if (settingsRef.get().await().exists()) {
                batch.update(
                    settingsRef,
                    mapOf(
                        "nextOrderCounter" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(orderDocRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(
        userId: String,
        orderId: String,
        newStatus: OrderStatus
    ): Result<Unit> {
        val docPath = "users/$userId/orders/$orderId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(orderId.isNotBlank()) { "Order ID must not be blank." }
            db.collection("users").document(uid)
                .collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun processOrderPayment(
        userId: String,
        order: CafeOrder,
        paymentMethod: PaymentMethod,
        tipCents: Long,
        discountCents: Long,
        paymentReference: String,
        markOrderCompleted: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/orders/${order.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(order.id.isNotBlank()) { "Order ID must not be blank." }
            require(paymentMethod != PaymentMethod.NONE) { "Select a valid payment method." }

            val cleanDiscount = discountCents.coerceIn(0L, order.subtotalCents)
            val cleanTip = tipCents.coerceIn(0L, 10_000_000L)
            val finalTotal = ((order.subtotalCents - cleanDiscount).coerceAtLeast(0L) + order.taxCents + cleanTip)
                .coerceIn(0L, 10_000_000L)

            val updates = mutableMapOf<String, Any>(
                "paymentStatus" to PaymentStatus.PAID.name,
                "paymentMethod" to paymentMethod.name,
                "tipCents" to cleanTip,
                "discountCents" to cleanDiscount,
                "totalCents" to finalTotal,
                "paymentReference" to paymentReference.trim().take(100),
                "paidAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (markOrderCompleted && order.parsedStatus != OrderStatus.CANCELLED) {
                updates["status"] = OrderStatus.COMPLETED.name
            }

            db.collection("users").document(uid)
                .collection("orders").document(order.id)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMenuItem(
        userId: String,
        name: String,
        category: MenuCategory,
        priceCents: Long,
        prepMinutes: Long
    ): Result<Unit> {
        var docPath = "users/$userId/menu_items"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Menu item name is required." }

            val docRef = db.collection("users").document(uid).collection("menu_items").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "category" to category.name,
                    "priceCents" to priceCents.coerceIn(0L, 1_000_000L),
                    "prepMinutes" to prepMinutes.coerceIn(1L, 120L),
                    "available" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMenuItem(
        userId: String,
        itemId: String,
        name: String,
        category: MenuCategory,
        priceCents: Long,
        prepMinutes: Long,
        available: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/menu_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(itemId.isNotBlank()) { "Menu item ID is required." }
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Menu item name is required." }

            db.collection("users").document(uid)
                .collection("menu_items").document(itemId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "category" to category.name,
                        "priceCents" to priceCents.coerceIn(0L, 1_000_000L),
                        "prepMinutes" to prepMinutes.coerceIn(1L, 120L),
                        "available" to available,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(userId: String, itemId: String): Result<Unit> {
        val docPath = "users/$userId/menu_items/$itemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(itemId.isNotBlank()) { "Menu item ID is required." }
            db.collection("users").document(uid)
                .collection("menu_items").document(itemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterMenu(userId: String): Result<Unit> {
        val path = "users/$userId/menu_items"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val starterItems = listOf(
                Triple("Masala Chai", MenuCategory.COFFEE, 6000L to 3L),
                Triple("South Indian Filter Coffee", MenuCategory.COFFEE, 9000L to 3L),
                Triple("Oat Flat White", MenuCategory.COFFEE, 22000L to 4L),
                Triple("Iced Matcha Latte", MenuCategory.COLD_DRINKS, 26000L to 3L),
                Triple("Cold Brew Tonic", MenuCategory.COLD_DRINKS, 24000L to 2L),
                Triple("Almond Croissant", MenuCategory.BAKERY, 19000L to 3L),
                Triple("Butter Bun Maska", MenuCategory.BAKERY, 11000L to 3L),
                Triple("Paneer Tikka Focaccia", MenuCategory.KITCHEN, 28000L to 8L),
                Triple("Avocado Sourdough", MenuCategory.KITCHEN, 32000L to 8L)
            )
            val batch = db.batch()
            val col = db.collection("users").document(uid).collection("menu_items")
            for ((name, category, pricePrep) in starterItems) {
                val docRef = col.document()
                batch.set(
                    docRef,
                    mapOf(
                        "userId" to uid,
                        "name" to name,
                        "category" to category.name,
                        "priceCents" to pricePrep.first,
                        "prepMinutes" to pricePrep.second,
                        "available" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addQrTable(
        userId: String,
        tableCode: String,
        label: String,
        linkedUrl: String
    ): Result<Unit> {
        var docPath = "users/$userId/qr_tables"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanCode = tableCode.trim().uppercase().take(20)
            val cleanLabel = label.trim().take(40)
            val cleanUrl = linkedUrl.trim().take(300)
            require(cleanCode.isNotEmpty()) { "Table code is required." }
            require(cleanLabel.isNotEmpty()) { "Table label is required." }
            require(cleanUrl.isNotEmpty()) { "Linked QR URL is required." }

            val docRef = db.collection("users").document(uid).collection("qr_tables").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "tableCode" to cleanCode,
                    "label" to cleanLabel,
                    "linkedUrl" to cleanUrl,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleQrTableActive(
        userId: String,
        tableId: String,
        active: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/qr_tables/$tableId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(tableId.isNotBlank()) { "Table ID is required." }
            db.collection("users").document(uid)
                .collection("qr_tables").document(tableId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteQrTable(userId: String, tableId: String): Result<Unit> {
        val docPath = "users/$userId/qr_tables/$tableId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(tableId.isNotBlank()) { "Table ID is required." }
            db.collection("users").document(uid)
                .collection("qr_tables").document(tableId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultQrTables(userId: String, baseWebsiteUrl: String): Result<Unit> {
        val path = "users/$userId/qr_tables"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val defaults = listOf(
                "T1" to "Table 1",
                "T2" to "Table 2",
                "T3" to "Table 3",
                "COUNTER" to "Pickup Counter"
            )
            val batch = db.batch()
            val col = db.collection("users").document(uid).collection("qr_tables")
            for ((code, label) in defaults) {
                val docRef = col.document()
                batch.set(
                    docRef,
                    mapOf(
                        "userId" to uid,
                        "tableCode" to code,
                        "label" to label,
                        "linkedUrl" to buildTableOrderingUrl(baseWebsiteUrl, code),
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncPortalOrdersToFirestore(
        userId: String,
        portalOrders: List<CafeOrder>,
        existingOrdersById: Map<String, CafeOrder>
    ): Result<Unit> {
        val path = "users/$userId/orders"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            if (portalOrders.isEmpty()) return Result.success(Unit)

            val ordersCol = db.collection("users").document(uid).collection("orders")
            val nowMs = System.currentTimeMillis()
            val batch = db.batch()
            var ops = 0

            for (portalOrder in portalOrders) {
                if (portalOrder.id.isBlank()) continue
                val existing = existingOrdersById[portalOrder.id]
                val docRef = ordersCol.document(portalOrder.id)

                if (existing == null) {
                    val createdMs = portalOrder.createdAt?.toDate()?.time
                    val safeCreatedAt: Any = if (createdMs != null && createdMs <= nowMs - 2000L) {
                        Timestamp(Date(createdMs))
                    } else {
                        FieldValue.serverTimestamp()
                    }
                    val payload = mutableMapOf<String, Any>(
                        "userId" to uid,
                        "orderNumber" to portalOrder.orderNumber.take(20),
                        "tableOrCustomer" to portalOrder.tableOrCustomer.take(60),
                        "orderType" to portalOrder.orderType,
                        "status" to portalOrder.status,
                        "paymentStatus" to portalOrder.paymentStatus,
                        "paymentMethod" to portalOrder.paymentMethod,
                        "items" to portalOrder.items.take(25).map { it.toFirestoreMap() },
                        "subtotalCents" to portalOrder.subtotalCents.coerceIn(0L, 10_000_000L),
                        "taxCents" to portalOrder.taxCents.coerceIn(0L, 10_000_000L),
                        "tipCents" to portalOrder.tipCents.coerceIn(0L, 10_000_000L),
                        "discountCents" to portalOrder.discountCents.coerceIn(0L, 10_000_000L),
                        "totalCents" to portalOrder.totalCents.coerceIn(0L, 10_000_000L),
                        "notes" to portalOrder.notes.take(300),
                        "paymentReference" to portalOrder.paymentReference.take(100),
                        "createdAt" to safeCreatedAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (portalOrder.parsedPaymentStatus == PaymentStatus.PAID) {
                        payload["paidAt"] = FieldValue.serverTimestamp()
                    }
                    batch.set(docRef, payload)
                    ops++
                } else {
                    val isTerminal = existing.parsedStatus == OrderStatus.CANCELLED ||
                        (existing.parsedStatus == OrderStatus.COMPLETED && existing.parsedPaymentStatus == PaymentStatus.PAID)
                    if (!isTerminal && existing.status != portalOrder.status &&
                        isForwardPortalStatusTransition(existing.parsedStatus, portalOrder.parsedStatus)
                    ) {
                        batch.update(
                            docRef,
                            mapOf(
                                "status" to portalOrder.status,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        ops++
                    }
                }

                if (ops >= 400) break
            }

            if (ops > 0) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncPortalOrdersFromBackground(
        userId: String,
        portalOrders: List<CafeOrder>
    ): Result<Unit> {
        if (portalOrders.isEmpty()) return Result.success(Unit)
        val path = "users/$userId/orders"
        return try {
            val uid = requireUserId()
            if (uid != userId) return Result.success(Unit)
            val snapshot = db.collection("users").document(uid)
                .collection("orders")
                .get()
                .await()
            val existingById = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CafeOrder::class.java)?.copy(id = doc.id)
            }.associateBy { it.id }
            syncPortalOrdersToFirestore(
                userId = userId,
                portalOrders = portalOrders,
                existingOrdersById = existingById
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun syncAmpleQrTables(
        userId: String,
        tableCount: Int,
        baseWebsiteUrl: String,
        existingTables: List<QrTableStation>
    ): Result<Unit> {
        val path = "users/$userId/qr_tables"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val safeCount = tableCount.coerceIn(1, 50)
            val existingByCode = existingTables.associateBy { it.tableCode.uppercase() }
            val col = db.collection("users").document(uid).collection("qr_tables")
            val batch = db.batch()
            var ops = 0

            for (num in 1..safeCount) {
                val code = "T$num"
                val expectedUrl = buildTableOrderingUrl(baseWebsiteUrl, code)
                val existing = existingByCode[code] ?: existingByCode["$num"]
                if (existing == null) {
                    val docRef = col.document("table_$num")
                    batch.set(
                        docRef,
                        mapOf(
                            "userId" to uid,
                            "tableCode" to code,
                            "label" to "Table $num",
                            "linkedUrl" to expectedUrl,
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ops++
                } else if (existing.linkedUrl != expectedUrl &&
                    (existing.linkedUrl.contains("?table=") || existing.linkedUrl.contains("ample.run"))
                ) {
                    batch.update(
                        col.document(existing.id),
                        mapOf(
                            "tableCode" to code,
                            "label" to existing.label.ifBlank { "Table $num" },
                            "linkedUrl" to expectedUrl,
                            "active" to existing.active,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    ops++
                }
            }

            if (ops > 0) {
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun isForwardPortalStatusTransition(oldStatus: OrderStatus, newStatus: OrderStatus): Boolean {
        return when (oldStatus) {
            OrderStatus.NEW -> newStatus in setOf(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED)
            OrderStatus.PREPARING -> newStatus in setOf(OrderStatus.READY, OrderStatus.COMPLETED)
            OrderStatus.READY -> newStatus == OrderStatus.COMPLETED
            OrderStatus.COMPLETED, OrderStatus.CANCELLED -> false
        }
    }
}
