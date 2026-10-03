package com.fixmoney.shared.home

import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.platform.dayOfMonth
import com.fixmoney.shared.platform.daysInMonth
import com.fixmoney.shared.platform.isoToEpochDay
import com.fixmoney.shared.platform.monthOf
import com.fixmoney.shared.platform.plusDaysIso
import com.fixmoney.shared.platform.todayIso

/**
 * The Home "AI INSIGHT" card's content. Port of the old app's `HomeInsight` -
 * every field is real derived data or absent, never a filler value, so
 * [HomeInsightEngine] returns null outright rather than invent one.
 */
data class HomeInsight(
    val categoryName: String?,
    val categoryPercentChange: Int?,
    val projectedOverageDay: Int?,
    val daysInMonth: Int,
    val dayOfMonth: Int
) {
    val hasCategoryTrend: Boolean get() = categoryName != null && categoryPercentChange != null
    val hasForecast: Boolean get() = projectedOverageDay != null

    val monthProgressFraction: Float get() = dayOfMonth.toFloat() / daysInMonth
    val overageFraction: Float? get() = projectedOverageDay?.let { it.toFloat() / daysInMonth }
}

private const val BASELINE_WEEKS = 3
private const val MIN_PERCENT_INCREASE = 20

/**
 * Computed from local rows - **no AI call**, despite the card's name, so it is
 * free, instant, and costs no Groq quota on every Home load. That was true of
 * the old app's engine too and is carried over verbatim, including both
 * thresholds.
 *
 * Returns null whenever there is no real signal (not enough history, spending
 * flat or down, on track within budget) rather than show something fabricated.
 *
 * Unlike the old version this takes the rows it needs rather than a repository:
 * Home already observes the month's expenses and budgets, so handing them over
 * avoids a second set of queries that could disagree with what is on screen.
 */
object HomeInsightEngine {

    fun compute(
        allExpenses: List<ExpenseEntity>,
        monthSpent: Int,
        monthBudget: Int,
        today: String = todayIso()
    ): HomeInsight? {
        val month = monthOf(today)
        val days = daysInMonth(month)
        val day = dayOfMonth(today)

        val weekStart = plusDaysIso(today, -6)
        val baselineEnd = plusDaysIso(weekStart, -1)
        val baselineStart = plusDaysIso(baselineEnd, -(BASELINE_WEEKS * 7 - 1))

        val currentWeek = allExpenses.inRange(weekStart, today)
        val baseline = allExpenses.inRange(baselineStart, baselineEnd)

        val categoryTrend = biggestCategoryIncrease(currentWeek, baseline)
        val overageDay = projectOverageDay(monthSpent, monthBudget, day, days)

        if (categoryTrend == null && overageDay == null) return null

        return HomeInsight(
            categoryName = categoryTrend?.first,
            categoryPercentChange = categoryTrend?.second,
            projectedOverageDay = overageDay,
            daysInMonth = days,
            dayOfMonth = day
        )
    }

    /**
     * At the current daily rate, the day of the month the budget runs out -
     * reported only when that lands **after today and still inside the month**,
     * which is the only case where it is news.
     */
    private fun projectOverageDay(spent: Int, budget: Int, day: Int, days: Int): Int? {
        if (budget <= 0 || spent <= 0 || day <= 0) return null
        val dailyRate = spent.toDouble() / day
        val crossover = budget / dailyRate
        return if (crossover > day && crossover < days) crossover.toInt() else null
    }

    /** The category up the most against its own 3-week weekly average. */
    private fun biggestCategoryIncrease(
        current: List<ExpenseEntity>,
        baseline: List<ExpenseEntity>
    ): Pair<String, Int>? {
        val currentByCategory = current.groupBy { it.categoryName }
            .mapValues { (_, rows) -> rows.sumOf { it.amount.toIntOrNull() ?: 0 } }
        val baselineWeeklyAverage = baseline.groupBy { it.categoryName }
            .mapValues { (_, rows) -> rows.sumOf { it.amount.toIntOrNull() ?: 0 } / BASELINE_WEEKS }

        return currentByCategory.mapNotNull { (name, amount) ->
            val average = baselineWeeklyAverage[name] ?: return@mapNotNull null
            if (average <= 0) return@mapNotNull null
            val percentChange = ((amount - average) * 100.0 / average).toInt()
            if (percentChange >= MIN_PERCENT_INCREASE) name to percentChange else null
        }.maxByOrNull { it.second }
    }
}

/** Rows whose `date` falls in [from, to] inclusive. */
internal fun List<ExpenseEntity>.inRange(from: String, to: String): List<ExpenseEntity> {
    val start = isoToEpochDay(from) ?: return emptyList()
    val end = isoToEpochDay(to) ?: return emptyList()
    return filter { row ->
        val d = isoToEpochDay(row.date) ?: return@filter false
        d in start..end
    }
}
