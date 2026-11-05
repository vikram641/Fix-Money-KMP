package com.fixmoney.shared.ai

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LenientAiJsonTest {
    @Serializable
    private data class P(@Serializable(with = LenientIntSerializer::class) val amount: Int = 0, val note: String? = null)

    private fun decode(raw: String) = LenientAiJson.decodeFromString<P>(extractJsonObject(raw))

    @Test
    fun modelQuirksDoNotThrow() {
        assertEquals(2599, decode("""{"amount": 2598.61}""").amount)
        assertEquals(500, decode("""{"amount": "500"}""").amount)
        assertEquals(42, decode("```json\n{\"amount\": 42, \"note\": \"x\"}\n```").amount)
        assertEquals(7, decode("Sure! {\"amount\": 7} hope that helps").amount)
    }

    @Test
    fun onlyRealDatesPass() {
        assertEquals("2026-02-28", validIsoDateOrNull("2026-02-28"))
        assertEquals("2028-02-29", validIsoDateOrNull("2028-02-29"))
        assertNull(validIsoDateOrNull("2026-02-30"))
        assertNull(validIsoDateOrNull("26/10/2026"))
        assertNull(validIsoDateOrNull("2026-13-01"))
    }
}
