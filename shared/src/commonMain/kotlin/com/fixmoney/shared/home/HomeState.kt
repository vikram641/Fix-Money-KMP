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
data class HomeUiState(
    val greeting: String,
    val initials: String,
    val month: String,
    val currencySymbol: String,
    val totalSpent: Int,
    val weekSpent: Int,
    val totalBudget: Int
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
        return HomeUiState(
            greeting = "${greetingForHour()}\n${name?.takeIf { it.isNotBlank() } ?: "there"} 👋",
            initials = initialsFrom(name),
            month = month,
            currencySymbol = CurrencyConstants.symbolFor(prefs.getCurrencyCode()),
            totalSpent = spent,
            weekSpent = 0,
            totalBudget = prefs.getMonthlyBudget()
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
