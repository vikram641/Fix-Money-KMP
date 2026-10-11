package com.fixmoney.shared.model

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    @AfterTest fun reset() { MoneyFormat.currencyCode = { "INR" } }

    @Test
    fun rupeeGrouping() {
        MoneyFormat.currencyCode = { "INR" }
        assertEquals("999", formatGrouped(999))
        assertEquals("1,000", formatGrouped(1_000))
        assertEquals("1,00,000", formatGrouped(100_000))
        assertEquals("12,34,567", formatGrouped(1_234_567))
        assertEquals("-12,34,567", formatGrouped(-1_234_567))
        assertEquals("₹12.4k", compactAmount("₹", 12_480))
        assertEquals("₹25.0L", compactAmount("₹", 2_500_000))
        assertEquals("₹1.0cr", compactAmount("₹", 10_000_629))
        assertEquals("₹940", compactAmount("₹", 940))
    }

    @Test
    fun westernGroupingForEveryoneElse() {
        MoneyFormat.currencyCode = { "USD" }
        assertEquals("1,234,567", formatGrouped(1_234_567))
        assertEquals("10,000,629", formatGrouped(10_000_629))
        assertEquals("130,856,022,124", formatGrouped(130_856_022_124))
        assertEquals("$10.0M", compactAmount("$", 10_000_629))
        assertEquals("$2.1B", compactAmount("$", 2_147_483_647))
        assertEquals("$12.4k", compactAmount("$", 12_480))
    }
}
