package com.fixmoney.shared.voice

import com.fixmoney.shared.voice.LocalVoiceParser.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalVoiceParserTest {

    private val names = listOf("Food", "Transport", "Shopping", "Health", "Bills", "Entertainment", "Other")

    private fun parsed(text: String): Triple<Int, String?, String> {
        val r = LocalVoiceParser.parse(text, names)
        check(r is Result.Parsed) { "\"$text\" -> $r" }
        return Triple(r.expense.amount, r.expense.category, r.expense.note)
    }

    @Test
    fun easyPhrasesAreReadLocally() {
        assertEquals(Triple(150, "Food", "Coffee"), parsed("150 coffee"))
        assertEquals(Triple(150, "Food", "Coffee"), parsed("150 on coffee."))
        assertEquals(Triple(40, "Transport", "Auto"), parsed("spent 40 rupees on auto"))
        assertEquals(Triple(500, "Food", "Groceries"), parsed("₹500 for groceries"))
        assertEquals(Triple(1200, "Bills", "Electricity bill"), parsed("add 1,200 electricity bill"))
        assertEquals(Triple(2000, "Shopping", "Shoes"), parsed("2k shoes"))
        assertEquals(Triple(80, "Transport", "Auto"), parsed("auto ke 80 diye"))
        assertEquals(Triple(300, "Health", "Health"), parsed("300 health"))
    }

    @Test
    fun aBareNumberIsFiledUnderOther() {
        assertEquals(Triple(250, "Other", ""), parsed("250"))
        assertEquals(Triple(250, "Other", ""), parsed("Rs. 250"))
    }

    @Test
    fun noNumberAtAllIsNoAmount() {
        assertEquals(Result.NoAmount, LocalVoiceParser.parse("coffee with friends", names))
        assertEquals(Result.NoAmount, LocalVoiceParser.parse("", names))
    }

    @Test
    fun anythingLessClearGoesToTheModel() {
        listOf(
            "one fifty coffee",          // number words
            "do sau ka petrol",          // Hindi number words
            "150 coffee and 40 auto",    // two numbers
            "coffee and sandwich 200",   // "and"
            "99.50 coffee",              // decimal
            "150 dinner split with Rahul",
            "500 at the new place downtown", // nothing names a category
            "200 coffee auto",           // two categories
            "yesterday 150 coffee",      // a date the model must read
        ).forEach { assertEquals(Result.Unsure, LocalVoiceParser.parse(it, names), it) }
    }

    @Test
    fun keywordsOnlyMapToCategoriesThatExist() {
        assertEquals(Result.Unsure, LocalVoiceParser.parse("150 coffee", listOf("Transport", "Other")))
    }
}
