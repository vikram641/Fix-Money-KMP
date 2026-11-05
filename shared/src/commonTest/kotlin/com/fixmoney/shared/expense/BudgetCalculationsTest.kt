package com.fixmoney.shared.expense

import com.fixmoney.shared.testutil.FakeBudgetDao
import com.fixmoney.shared.testutil.FakeExpenseDao
import com.fixmoney.shared.testutil.budget
import com.fixmoney.shared.testutil.expense
import com.fixmoney.shared.testutil.prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetCalculationsTest {

    private fun envelope(name: String, limit: Int, spent: Int) =
        BudgetProgress(id = name, categoryId = name, categoryName = name, categoryColor = "#000", categoryIcon = "", limit = limit, spent = spent)

    private val factory = BudgetStateFactory(prefs(), BudgetRepository(FakeBudgetDao(), FakeExpenseDao()), FakeExpenseDao())

    @Test
    fun capsSpendRemainingAndRunway() {
        val s = factory.build(listOf(envelope("Food", 6_000, 3_300), envelope("Bills", 4_000, 1_100)), "2026-10", "2026-10-11")
        assertEquals(10_000, s.totalCap)
        assertEquals(4_400, s.totalSpent)
        assertEquals(5_600, s.remaining)
        assertEquals(21, s.daysLeft)
        assertEquals(400, s.dailyBurn)       // 4,400 / 11 days
        assertEquals(266, s.dailyRunway)     // 5,600 / 21 days left
    }

    @Test
    fun envelopePercentClampsForTheBarButNotForTheLabel() {
        val over = envelope("Food", 1_000, 2_500)
        assertEquals(100, over.percent)
        assertEquals(250, over.rawPercent)
        assertTrue(over.isOver)
        assertEquals(0, envelope("Food", 0, 500).percent)   // no divide by zero
    }

    @Test
    fun guardFiresFromSeventyFivePercent() {
        assertNull(factory.build(listOf(envelope("Food", 1_000, 740)), "2026-10", "2026-10-11").guard)
        val g = factory.build(listOf(envelope("Food", 1_000, 750), envelope("Bills", 1_000, 100)), "2026-10", "2026-10-11").guard
        assertEquals("Food", g?.categoryName)
        assertEquals(75, g?.percentUsed)
        assertFalse(g!!.isOver)
        assertEquals(500, factory.build(listOf(envelope("Food", 1_000, 1_500)), "2026-10", "2026-10-11").guard?.overspend)
    }

    @Test
    fun futureAndPastMonthsHaveSensibleDays() {
        assertEquals(30, factory.build(emptyList(), "2026-11", "2026-10-11").daysLeft) // not started yet
        assertEquals(0, factory.build(emptyList(), "2026-09", "2026-10-11").daysLeft)
    }

    @Test
    fun spentPerEnvelopeCountsOnlyItsOwnCategoryAndCurrency() = runTest {
        val budgets = FakeBudgetDao(listOf(budget("Food", 5_000, "2026-10", "INR")))
        val expenses = FakeExpenseDao(listOf(
            expense("2026-10-02", "1200", "Food", "INR"),
            expense("2026-10-03", "12.50", "Food", "INR"),   // decimal -> 13
            expense("2026-10-04", "40", "Food", "USD"),      // other currency, not against a ₹ cap
            expense("2026-10-05", "999", "Bills", "INR"),    // other category
            expense("2026-09-30", "700", "Food", "INR"),     // other month
        ))
        val progress = BudgetRepository(budgets, expenses).observeForMonth("2026-10").first().single()
        assertEquals(1_213, progress.spent)
        assertEquals(24, progress.percent)
    }
}
