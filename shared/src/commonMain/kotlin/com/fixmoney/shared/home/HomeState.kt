package com.fixmoney.shared.home

import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentHour
import com.fixmoney.shared.platform.currentMonthApi

/**
 * What the Home screen shows. The old `HomeViewModel` pulled spend figures from
 * Room and the server; neither exists here yet, so [totalSpent] and [weekSpent]
 * are 0 and the screen renders its empty state - exactly what a fresh install
 * saw in the old app too.
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

class HomeStateFactory(private val prefs: OnboardingPrefs) {

    fun build(): HomeUiState {
        val name = prefs.getUserName()
        return HomeUiState(
            greeting = "${greetingForHour()}\n${name?.takeIf { it.isNotBlank() } ?: "there"} 👋",
            initials = initialsFrom(name),
            month = currentMonthApi(),
            currencySymbol = CurrencyConstants.symbolFor(prefs.getCurrencyCode()),
            totalSpent = 0,
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
