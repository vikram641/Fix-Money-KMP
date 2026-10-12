package com.fixmoney.shared.calc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SipMathTest {
    @Test
    fun standardTwelvePercentTenYears() {
        val r = sipProjection(5_000, 12.0, 10)
        assertEquals(600_000, r.invested)
        // Known value for an annuity-due SIP: ₹11,61,695 (±₹2 for rounding)
        assertTrue(abs(r.futureValue - 1_161_695) <= 2, "got ${r.futureValue}")
        assertEquals(r.futureValue - r.invested, r.returns)
        assertEquals(93, r.gainPercent)
    }

    @Test
    fun zeroRateIsJustTheContributions() {
        val r = sipProjection(1_000, 0.0, 5)
        assertEquals(60_000, r.futureValue)
        assertEquals(0, r.returns)
    }

    @Test
    fun hugeInputsNeverWrapNegative() {
        val r = sipProjection(100_000, 30.0, 35)
        assertTrue(r.futureValue > Int.MAX_VALUE.toLong(), "got ${r.futureValue}")
        assertTrue(r.returns > 0 && r.gainPercent > 0)
    }

    @Test
    fun emptyInputs() {
        assertEquals(SipResult(0, 0, 0, 0), sipProjection(0, 12.0, 10))
        assertEquals(SipResult(0, 0, 0, 0), sipProjection(5_000, 12.0, 0))
    }
}
