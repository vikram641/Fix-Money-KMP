package com.fixmoney.shared.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DateMathTest {
    @Test
    fun daysInMonthHandlesLeapYears() {
        assertEquals(29, daysInMonth("2024-02"))
        assertEquals(28, daysInMonth("2026-02"))
        assertEquals(28, daysInMonth("2100-02"))
        assertEquals(29, daysInMonth("2000-02"))
        assertEquals(31, daysInMonth("2026-10"))
        assertEquals(30, daysInMonth("2026-11"))
    }

    @Test
    fun addingDaysCrossesMonthsAndYears() {
        assertEquals("2027-01-01", plusDaysIso("2026-12-31", 1))
        assertEquals("2024-02-29", plusDaysIso("2024-03-01", -1))
        assertEquals("2026-09-27", plusDaysIso("2026-10-03", -6))
        assertEquals(365L, daysBetween("2026-01-01", "2027-01-01"))
        assertEquals(366L, daysBetween("2024-01-01", "2025-01-01"))
    }
}
