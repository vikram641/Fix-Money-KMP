package com.fixmoney.shared.analytics

import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.testutil.FakeBudgetDao
import com.fixmoney.shared.testutil.FakeExpenseDao
import com.fixmoney.shared.testutil.expense
import com.fixmoney.shared.testutil.prefs
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsCalculationsTest {

    private val factory = AnalyticsStateFactory(prefs(), ExpenseRepository(FakeExpenseDao()), FakeBudgetDao())
    private val today = "2026-10-11"

    @Test
    fun monthTotalsBurnAndProjection() {
        val rows = listOf(expense("2026-10-01", "600"), expense("2026-10-10", "500"), expense("2026-09-15", "900"))
        val s = factory.build(rows, "2026-10", AnalyticsScope.Month, 3_000, today)
        assertEquals(1_100, s.total)
        assertEquals(900, s.previousTotal)
        assertEquals(100, s.dailyBurn)      // 1,100 / 11
        assertEquals(3_100, s.projected)    // 100 x 31
        assertEquals(3_000, s.budgetLimit)
    }

    @Test
    fun quarterAddsThreeMonthsAndBudgetsForThree() {
        val rows = listOf(expense("2026-08-10", "1000"), expense("2026-09-10", "1000"), expense("2026-10-10", "1100"))
        val s = factory.build(rows, "2026-10", AnalyticsScope.Quarter, 3_000, today)
        assertEquals(3_100, s.total)
        assertEquals(9_000, s.budgetLimit)
    }

    @Test
    fun quarterInsightUsesOnlyThisMonthsSpend() {
        // Big August/September must not make October look over budget.
        val rows = listOf(expense("2026-08-10", "50000"), expense("2026-09-10", "50000"), expense("2026-10-05", "1100"))
        val month = factory.build(rows, "2026-10", AnalyticsScope.Month, 3_000, today).insight?.projectedOverageDay
        val quarter = factory.build(rows, "2026-10", AnalyticsScope.Quarter, 3_000, today).insight?.projectedOverageDay
        assertEquals(30, month)      // 100/day -> 3,000 on day 30
        assertEquals(month, quarter) // was "day 1" from 101,100 of quarter spend
    }

    @Test
    fun otherCurrenciesAreLeftOut() {
        val rows = listOf(expense("2026-10-01", "1000", currency = "INR"), expense("2026-10-02", "50", currency = "USD"))
        assertEquals(1_000, factory.build(rows, "2026-10", AnalyticsScope.Month, 0, today).total)
    }
}
