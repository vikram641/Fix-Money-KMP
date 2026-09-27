package com.fixmoney.shared.home

import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentHour
import com.fixmoney.shared.platform.currentMonthApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the Home screen shows. Spend comes from Room now; [weekSpent] is still
 * 0 because the week window needs real date arithmetic, which this build's
 * deliberately minimal date layer (`platform/DateTime.kt`) does not do yet.
 */
/** One slice of Home's donut: a category, its share of the month's spend. */
data class CategorySlice(
    val name: String,
    val colorHex: String,
    val amount: Int,
    val percent: Int
)

data class HomeUiState(
    val greeting: String,
    val initials: String,
    val month: String,
    val currencySymbol: String,
    val totalSpent: Int,
    val weekSpent: Int,
    val totalBudget: Int,
    val breakdown: List<CategorySlice> = emptyList()
) {
    val remaining: Int get() = (totalBudget - totalSpent).coerceAtLeast(0)

    /** Same integer percentage the old `Utils.calculatePercentage` produced. */
    val percentUsed: Int
        get() = if (totalBudget <= 0) 0 else ((totalSpent * 100L) / totalBudget).toInt().coerceIn(0, 100)

    val hasExpenses: Boolean get() = totalSpent > 0
}

class HomeStateFactory(
    private val prefs: OnboardingPrefs,
    private val expenses: ExpenseRepository
) {

    /** Re-emits whenever the expenses table changes, so a new expense shows up
     * on Home without a manual refresh - same behaviour as the old
     * `HomeViewModel.recentExpenses`. */
    fun observe(month: String = currentMonthApi()): Flow<HomeUiState> =
        expenses.observeMonth(month).map { rows -> build(rows, month) }

    fun build(
        rows: List<ExpenseEntity> = emptyList(),
        month: String = currentMonthApi()
    ): HomeUiState {
        val name = prefs.getUserName()
        val spent = rows.sumOf { it.amount.toIntOrNull() ?: 0 }

        // Grouped by category for the donut, biggest first - the same breakdown
        // the old Home showed via MPAndroidChart's pie + legend.
        val slices = rows
            .groupBy { it.categoryName }
            .map { (name, group) ->
                val amount = group.sumOf { it.amount.toIntOrNull() ?: 0 }
                CategorySlice(
                    name = name,
                    colorHex = group.first().categoryColor,
                    amount = amount,
                    percent = if (spent <= 0) 0 else ((amount * 100L) / spent).toInt()
                )
            }
            .sortedByDescending { it.amount }
            .let { list ->
                // Integer division loses a point or two (87% + 12% = 99%), which
                // shows as a gap in the donut. Give the remainder to the biggest
                // slice so the labels and the ring both add up to 100.
                val gap = 100 - list.sumOf { it.percent }
                if (gap == 0 || list.isEmpty()) list
                else list.mapIndexed { i, slice ->
                    if (i == 0) slice.copy(percent = slice.percent + gap) else slice
                }
            }
        return HomeUiState(
            greeting = "${greetingForHour()}\n${name?.takeIf { it.isNotBlank() } ?: "there"} 👋",
            initials = initialsFrom(name),
            month = month,
            currencySymbol = CurrencyConstants.symbolFor(prefs.getCurrencyCode()),
            totalSpent = spent,
            weekSpent = 0,
            totalBudget = prefs.getMonthlyBudget(),
            breakdown = slices
        )
    }

    /** Same buckets as the old `HomeViewModel.getGreeting()`. */
    private fun greetingForHour(): String = when (currentHour()) {
        in 0..11 -> "Good Morning"
        in 12..15 -> "Good Afternoon"
        in 16..20 -> "Good Evening"
        else -> "Good Night"
    }

    private fun initialsFrom(name: String?): String =
        name.orEmpty().split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
}
