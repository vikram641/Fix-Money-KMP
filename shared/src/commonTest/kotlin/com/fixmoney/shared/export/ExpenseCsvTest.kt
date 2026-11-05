package com.fixmoney.shared.export

import com.fixmoney.shared.testutil.expense
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseCsvTest {
    @Test
    fun escapingAndFormulaInjection() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a, b\"", csvField("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", csvField("line1\nline2"))
        assertEquals("'=HYPERLINK(\"x\")".let { "\"${it.replace("\"", "\"\"")}\"" }, csvField("=HYPERLINK(\"x\")"))
        assertEquals("'+91 98765", csvField("+91 98765"))
        assertEquals("'@cmd", csvField("@cmd"))
    }

    @Test
    fun headerAndRows() {
        val csv = buildExpenseCsv(listOf(expense("2026-10-01", "450", "Food", "INR", "Lunch, team")))
        assertEquals("Date,Category,Amount,Currency,Note\n2026-10-01,Food,450,INR,\"Lunch, team\"\n", csv)
    }
}
