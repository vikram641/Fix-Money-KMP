package com.fixmoney.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseAmountTest {
    @Test
    fun oldAppFormatsAreRead() {
        assertEquals(450, parseStoredAmount("450"))
        assertEquals(13, parseStoredAmount("12.50"))   // was 0, and 1250 after an edit
        assertEquals(1250, parseStoredAmount("₹1,250"))
        assertEquals(1250, parseStoredAmount(" 1 250 "))
        assertEquals(0, parseStoredAmount(""))
        assertEquals(0, parseStoredAmount("abc"))
    }
}
