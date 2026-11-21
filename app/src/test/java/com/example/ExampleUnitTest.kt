package com.example

import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.PaymentMethod
import com.example.util.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatMoney_formatsRupeesAndCentsAccurately() {
        assertEquals("₹60", formatMoney(6000L, "₹"))
        assertEquals("₹125.05", formatMoney(12505L, "₹"))
        assertEquals("₹0", formatMoney(0L, "₹"))
        assertEquals("$4.50", formatMoney(450L, "$"))
    }

    @Test
    fun enumParsing_fallsBackSafelyOnUnknownValues() {
        assertEquals(OrderStatus.NEW, OrderStatus.fromRaw("UNKNOWN_STATE"))
        assertEquals(OrderStatus.READY, OrderStatus.fromRaw("READY"))
        assertEquals(OrderType.QR_SCAN, OrderType.fromRaw(null))
        assertEquals(OrderType.QR_SCAN, OrderType.fromRaw("QR_SCAN"))
        assertEquals(PaymentMethod.NONE, PaymentMethod.fromRaw(null))
        assertEquals(PaymentMethod.UPI, PaymentMethod.fromRaw("UPI"))
    }
}
