package com.fixmoney.shared.voice

import com.fixmoney.shared.voice.LocalVoiceParser.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bugs found in the 2026-10-11 device pass. */
class VoiceRegressionTest {

    private val names = listOf("Food", "Transport", "Shopping", "Health", "Bills", "Entertainment", "Other")

    @Test
    fun aMisheardForIsNeverJoinedIntoTheAmount() {
        // "500 for t-shirt" came through as "500,4 t-shirt" and was saved as 5004.
        val r = LocalVoiceParser.parse("500,4 t-shirt", names)
        assertTrue(r !is Result.Parsed || r.expense.amount != 5004, "got $r")
    }

    @Test
    fun realDigitGroupingStillWorks() {
        val western = LocalVoiceParser.parse("1,250 shoes", names)
        val indian = LocalVoiceParser.parse("1,25,000 rent bills", names)
        assertEquals(1250, (western as Result.Parsed).expense.amount)
        assertTrue(indian !is Result.Parsed || indian.expense.amount == 125000, "got $indian")
    }

    @Test
    fun aNegativeOrZeroAmountIsNotSavedLocally() {
        assertTrue(LocalVoiceParser.parse("-200 refund", names) !is Result.Parsed)
        assertEquals(Result.NoAmount, LocalVoiceParser.parse("0 coffee", names))
    }
}
