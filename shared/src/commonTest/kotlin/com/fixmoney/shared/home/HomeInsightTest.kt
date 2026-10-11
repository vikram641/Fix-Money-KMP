package com.fixmoney.shared.home

import com.fixmoney.shared.testutil.expense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeInsightTest {

    @Test
    fun overageDayIsRoundedUpAndMustBeAfterToday() {
        // 1,200 by day 10 = 120/day -> a 3,000 budget runs out on day 25.
        assertEquals(25, HomeInsightEngine.compute(emptyList(), 1_200, 3_000, "2026-10-10")?.projectedOverageDay)
        // 114/day -> 26.3 -> the 27th, not the 26th (was truncated).
        assertEquals(27, HomeInsightEngine.compute(emptyList(), 1_140, 3_000, "2026-10-10")?.projectedOverageDay)
        // Crossing exactly today is not a forecast.
        assertNull(HomeInsightEngine.compute(emptyList(), 1_000, 1_000, "2026-10-10"))
        // On track: crossing after month end.
        assertNull(HomeInsightEngine.compute(emptyList(), 300, 3_000, "2026-10-10"))
        // No budget, nothing to forecast.
        assertNull(HomeInsightEngine.compute(emptyList(), 300, 0, "2026-10-10"))
    }

    @Test
    fun categoryTrendAgainstThreeRealBaselineWeeks() {
        // today 2026-10-21: this week = 10-15..10-21, baseline = 09-24..10-14
        val rows = listOf(
            expense("2026-09-25", "100"), expense("2026-10-02", "100"), expense("2026-10-09", "100"),
            expense("2026-10-20", "200"),
        )
        val insight = HomeInsightEngine.compute(rows, 0, 0, "2026-10-21")
        assertEquals("Food", insight?.categoryName)
        assertEquals(100, insight?.categoryPercentChange) // 200 vs a 100/week average
    }

    @Test
    fun aBrandNewUserGetsNoFakeSpike() {
        // One week of history only: dividing by 3 used to say "+200%".
        val rows = listOf(expense("2026-10-13", "300"), expense("2026-10-20", "300"))
        assertNull(HomeInsightEngine.compute(rows, 0, 0, "2026-10-21"))
    }

    @Test
    fun smallIncreasesAreNotNews() {
        val rows = listOf(
            expense("2026-09-25", "100"), expense("2026-10-02", "100"), expense("2026-10-09", "100"),
            expense("2026-10-20", "110"),  // +10% < 20% threshold
        )
        assertNull(HomeInsightEngine.compute(rows, 0, 0, "2026-10-21"))
    }
}
