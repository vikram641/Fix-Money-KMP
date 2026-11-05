package com.fixmoney.shared.expense

import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.dayOfMonth
import com.fixmoney.shared.platform.daysInMonth
import com.fixmoney.shared.platform.monthOf
import com.fixmoney.shared.platform.todayIso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How an envelope is doing, for the word next to its percentage. */
enum class EnvelopeHealth { Safe, Healthy, Watch, Critical, Over }

/** The one thing worth saying about the month's caps, or nothing. */
data class SpendingGuard(
    val categoryName: String,
    val percentUsed: Int,
    val daysLeft: Int,
    val overspend: Int,
    val isOver: Boolean
)

data class BudgetUiState(
    val currencySymbol: String,
    val month: String,
    val totalCap: Int = 0,
    val totalSpent: Int = 0,
    val daysLeft: Int = 0,
    val envelopes: List<BudgetProgress> = emptyList(),
    val guard: SpendingGuard? = null,
    /** How far through the month we are, as a percentage. */
    val monthElapsedPercent: Int = 0
) {
    val hasCaps: Boolean get() = totalCap > 0

    val remaining: Int get() = totalCap - totalSpent

    val percentUsed: Int
        get() = if (totalCap <= 0) 0 else ((totalSpent * 100L) / totalCap).toInt()

    /**
     * Spend pace against calendar pace. Negative is good - it means less of the
     * cap is gone than of the month.
     */
    val paceDelta: Int get() = percentUsed - monthElapsedPercent

    val onPace: Boolean get() = totalCap > 0 && paceDelta <= PACE_GRACE

    /** Spend so far over days elapsed. */
    val dailyBurn: Int
        get() {
            val elapsed = elapsedDays
            return if (elapsed > 0) totalSpent / elapsed else 0
        }

    /** What is left, spread over the days left - the "runway" figure. */
    val dailyRunway: Int
        get() = if (daysLeft > 0) (remaining / daysLeft).coerceAtLeast(0) else 0

    internal var elapsedDays: Int = 1
}

/** How far ahead of the month's pace still counts as "on pace", in points. */
private const val PACE_GRACE = 10

/** Thresholds for the word beside an envelope's percentage. */
fun BudgetProgress.health(): EnvelopeHealth = when {
    isOver -> EnvelopeHealth.Over
    rawPercent >= 95 -> EnvelopeHealth.Critical
    rawPercent >= 75 -> EnvelopeHealth.Watch
    rawPercent >= 50 -> EnvelopeHealth.Healthy
    else -> EnvelopeHealth.Safe
}

/**
 * The Budget screen's figures, derived from the budget rows and the month's
 * expenses - the same two sources Home and Analytics read, so the caps and the
 * spend behind them can never disagree.
 */
class BudgetStateFactory(
    private val prefs: OnboardingPrefs,
    private val budgets: BudgetRepository,
    private val expenses: ExpenseDao
) {

    fun observe(month: String = currentMonthApi()): Flow<BudgetUiState> =
        budgets.observeForMonth(month).map { rows -> build(rows, month) }

    fun build(
        envelopes: List<BudgetProgress> = emptyList(),
        month: String = currentMonthApi(),
        todayOverride: String? = null
    ): BudgetUiState {
        val today = todayOverride ?: todayIso()
        val isCurrent = monthOf(today) == month
        val totalDays = daysInMonth(month)
        // A month still to come has not started; one in progress still has today.
        val isFuture = month > monthOf(today)
        val elapsed = when {
            isCurrent -> dayOfMonth(today)
            isFuture -> 0
            else -> totalDays
        }
        val daysLeft = when {
            isCurrent -> totalDays - elapsed + 1
            isFuture -> totalDays
            else -> 0
        }

        val ranked = envelopes.sortedByDescending { it.rawPercent }

        return BudgetUiState(
            currencySymbol = CurrencyConstants.symbolFor(prefs.getCurrencyCode()),
            month = month,
            totalCap = envelopes.sumOf { it.limit },
            totalSpent = envelopes.sumOf { it.spent },
            daysLeft = daysLeft,
            envelopes = ranked,
            guard = guard(ranked, daysLeft),
            monthElapsedPercent = (elapsed * 100) / totalDays
        ).also { it.elapsedDays = elapsed }
    }

    /**
     * The mockup's "Fixoo Spending Guard" - a single sentence about the cap most
     * at risk. Computed, not generated: it is the envelope with the highest
     * utilisation, reported only once it is past [GUARD_THRESHOLD].
     *
     * Returns null below that, rather than filling the card with a reassurance
     * nobody needs.
     */
    private fun guard(ranked: List<BudgetProgress>, daysLeft: Int): SpendingGuard? {
        val worst = ranked.firstOrNull() ?: return null
        if (worst.limit <= 0 || worst.rawPercent < GUARD_THRESHOLD) return null
        return SpendingGuard(
            categoryName = worst.categoryName,
            percentUsed = worst.rawPercent,
            daysLeft = daysLeft,
            overspend = (worst.spent - worst.limit).coerceAtLeast(0),
            isOver = worst.isOver
        )
    }

    private companion object {
        const val GUARD_THRESHOLD = 75
    }
}
