package com.fixmoney.shared.home

import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.MoneyFormat
import com.fixmoney.shared.testutil.FakeBudgetDao
import com.fixmoney.shared.testutil.FakeExpenseDao
import com.fixmoney.shared.testutil.expense
import com.fixmoney.shared.testutil.prefs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home hero card + donut maths against a fixed "today" of 2026-10-11 (31-day month). */
class HomeCalculationsTest {

    private fun factory(currency: String = "INR", budget: Int = 25_000) =
        HomeStateFactory(prefs(currency, budget), ExpenseRepository(FakeExpenseDao()), FakeBudgetDao())

    private val today = "2026-10-11"

    @Test
    fun spentAddsEveryRowOfTheMonthIncludingOldDecimalRows() {
        val rows = listOf(
            expense("2026-10-01", "1000"),
            expense("2026-10-05", "12.50"),        // old app row -> 13, was 0
            expense("2026-09-30", "999"),          // other month, not counted
        )
        val s = factory().build(rows, "2026-10", 0, today)
        assertEquals(1013, s.totalSpent)
    }

    @Test
    fun weekIsTheLastSevenDaysIncludingToday() {
        val rows = listOf(
            expense("2026-10-04", "100"),  // 8 days ago - out
            expense("2026-10-05", "200"),  // 6 days ago - in
            expense("2026-10-11", "300"),  // today - in
        )
        assertEquals(500, factory().build(rows, "2026-10", 0, today).weekSpent)
    }

    @Test
    fun weekReachesBackIntoLastMonthEarlyInTheMonth() {
        val rows = listOf(expense("2026-09-28", "400"), expense("2026-10-02", "100"))
        val s = factory().build(rows, "2026-10", 0, "2026-10-03")
        assertEquals(500, s.weekSpent)      // was 100 before the fix
        assertEquals(100, s.totalSpent)     // the month total stays October only
    }

    @Test
    fun daysLeftCountsToday() {
        assertEquals(21, factory().build(emptyList(), "2026-10", 0, "2026-10-11").daysLeft)
        assertEquals(1, factory().build(emptyList(), "2026-10", 0, "2026-10-31").daysLeft)   // was 0 / "Month closed"
        assertEquals(31, factory().build(emptyList(), "2026-10", 0, "2026-10-01").daysLeft)
        assertEquals(0, factory().build(emptyList(), "2026-09", 0, today).daysLeft)           // past month
    }

    @Test
    fun dailyAverageIsSpendOverElapsedDays() {
        val rows = listOf(expense("2026-10-02", "1100"))
        assertEquals(100, factory().build(rows, "2026-10", 0, today).dailyAverage)
    }

    @Test
    fun donutPercentagesAlwaysAddUpTo100() {
        val rows = listOf(
            expense("2026-10-01", "100", "Food"),
            expense("2026-10-01", "100", "Bills"),
            expense("2026-10-01", "100", "Shopping"),
        )
        val slices = factory().build(rows, "2026-10", 0, today).breakdown
        assertEquals(100, slices.sumOf { it.percent })
        assertEquals(listOf(34, 33, 33), slices.map { it.percent })
    }

    @Test
    fun budgetRowsWinOverTheOnboardingFigure() {
        assertEquals(30_000, factory(budget = 25_000).build(emptyList(), "2026-10", 30_000, today).totalBudget)
        assertEquals(25_000, factory(budget = 25_000).build(emptyList(), "2026-10", 0, today).totalBudget)
    }

    @Test
    fun otherCurrenciesAreNeverAddedIn() {
        val rows = listOf(
            expense("2026-10-02", "500", currency = "USD"),
            expense("2026-10-03", "200", currency = "USD"),
            expense("2026-10-04", "5000", currency = "INR"),  // recorded before switching to USD
        )
        val s = factory(currency = "USD").build(rows, "2026-10", 0, today)
        assertEquals(700, s.totalSpent)
        assertEquals(1, s.otherCurrencyCount)
    }

    @Test
    fun monthElapsedPercent() {
        assertEquals(35, factory().build(emptyList(), "2026-10", 0, today).monthElapsedPercent) // 11/31
    }
}
