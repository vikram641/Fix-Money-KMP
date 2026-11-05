package com.fixmoney.shared.analytics

import com.fixmoney.shared.db.inCurrency

import com.fixmoney.shared.db.amountValue

import com.fixmoney.shared.db.BudgetDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.home.HomeInsight
import com.fixmoney.shared.home.HomeInsightEngine
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.dayOfMonth
import com.fixmoney.shared.platform.daysInMonth
import com.fixmoney.shared.platform.isoToEpochDay
import com.fixmoney.shared.platform.monthOf
import com.fixmoney.shared.platform.todayIso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The period the Analytics screen is summarising. */
enum class AnalyticsScope { Month, Quarter, Year }

/** One bar of the velocity chart. [projected] bars are an extrapolation, not data. */
data class VelocityBucket(val label: String, val amount: Int, val projected: Boolean)

data class CategoryAllocation(
    val name: String,
    val colorHex: String,
    val icon: String,
    val amount: Int,
    val sharePercent: Int,
    val transactions: Int,
    /** Against the previous period of the same length; null when there is none. */
    val changeVsPreviousPercent: Int?
)

/** A payee - which in this schema is whatever the user typed in the note. */
data class PayeeTotal(
    val name: String,
    val categoryName: String,
    val colorHex: String,
    val icon: String,
    val amount: Int,
    val sharePercent: Int
)

data class AnalyticsUiState(
    val currencySymbol: String,
    val periodLabel: String,
    val scope: AnalyticsScope = AnalyticsScope.Month,
    val total: Int = 0,
    val previousTotal: Int = 0,
    val dailyBurn: Int = 0,
    val peakDayAmount: Int = 0,
    val peakDayLabel: String = "",
    val projected: Int = 0,
    val budgetLimit: Int = 0,
    val velocity: List<VelocityBucket> = emptyList(),
    val allocation: List<CategoryAllocation> = emptyList(),
    val payees: List<PayeeTotal> = emptyList(),
    val insight: HomeInsight? = null
) {
    val hasData: Boolean get() = total > 0

    /** The biggest category's share - the donut's centre figure. */
    val topSharePercent: Int get() = allocation.firstOrNull()?.sharePercent ?: 0
    val topCategory: CategoryAllocation? get() = allocation.firstOrNull()

    /** What the three listed payees add up to, as a share of the period. */
    val payeeShareOfTotal: Int
        get() = if (total <= 0) 0 else ((payees.sumOf { it.amount } * 100L) / total).toInt()

    /** "Consumes ~₹160 per every ₹1,000 processed this cycle." */
    val topCategoryPerThousand: Int
        get() = topCategory?.let { (it.sharePercent * 1000) / 100 } ?: 0
}

private const val PAYEE_LIMIT = 3

/**
 * Everything the Analytics screen shows, computed from the same two tables the
 * rest of the app reads. No API call and nothing stored - the screen is a pure
 * function of the expense rows, so it can never disagree with Expenses or Home.
 */
class AnalyticsStateFactory(
    private val prefs: OnboardingPrefs,
    private val expenses: ExpenseRepository,
    private val budgets: BudgetDao
) {

    fun observe(
        anchorMonth: String = currentMonthApi(),
        scope: AnalyticsScope = AnalyticsScope.Month
    ): Flow<AnalyticsUiState> =
        combine(
            // All rows: a quarter or a year reaches well outside the anchor
            // month, and the previous-period comparison reaches further still.
            expenses.observeExpenses(),
            budgets.observeForMonth(anchorMonth)
        ) { rows, budgetRows ->
            build(rows, anchorMonth, scope, budgetRows.sumOf { it.limitAmount })
        }

    fun build(
        allRowsAnyCurrency: List<ExpenseEntity> = emptyList(),
        anchorMonth: String = currentMonthApi(),
        scope: AnalyticsScope = AnalyticsScope.Month,
        monthBudget: Int = 0,
        todayOverride: String? = null
    ): AnalyticsUiState {
        val profileCurrency = prefs.getCurrencyCode()
        val symbol = CurrencyConstants.symbolFor(profileCurrency)
        // Never add ₹ rows to a $ total; see ExpenseEntity.isIn.
        val allRows = allRowsAnyCurrency.inCurrency(profileCurrency)
        val months = monthsIn(anchorMonth, scope)
        val previousMonths = monthsIn(shiftMonthKey(anchorMonth, -months.size), scope)

        val rows = allRows.filter { monthOf(it.date) in months }
        val previousRows = allRows.filter { monthOf(it.date) in previousMonths }

        val total = rows.sumOf { it.amountInt }
        val previousTotal = previousRows.sumOf { it.amountInt }

        val today = todayOverride ?: todayIso()
        val isCurrent = monthOf(today) in months
        // Days elapsed in the period: up to today when it is still running,
        // otherwise the whole thing. Dividing by the full length of a month that
        // is three days old would report a burn rate of almost nothing.
        val elapsedDays = if (isCurrent) {
            months.sumOf { m -> if (m == monthOf(today)) dayOfMonth(today) else daysInMonth(m) }
        } else {
            months.sumOf { daysInMonth(it) }
        }
        val totalDays = months.sumOf { daysInMonth(it) }
        val dailyBurn = if (elapsedDays > 0) total / elapsedDays else 0

        val byDay = rows.groupBy { it.date }.mapValues { (_, r) -> r.sumOf { it.amountInt } }
        val peak = byDay.maxByOrNull { it.value }

        return AnalyticsUiState(
            currencySymbol = symbol,
            periodLabel = periodLabel(anchorMonth, scope),
            scope = scope,
            total = total,
            previousTotal = previousTotal,
            dailyBurn = dailyBurn,
            peakDayAmount = peak?.value ?: 0,
            peakDayLabel = peak?.key?.let { ordinalDay(it) }.orEmpty(),
            // Straight-line from the rate so far. Only meaningful mid-period;
            // a finished period's projection is just its total.
            projected = if (isCurrent) dailyBurn * totalDays else total,
            budgetLimit = if (scope == AnalyticsScope.Month) monthBudget else monthBudget * months.size,
            velocity = velocity(rows, months, scope, dailyBurn, today, isCurrent),
            allocation = allocation(rows, previousRows, total),
            payees = payees(rows, total),
            // Always this month's spend against this month's budget. Passing a
            // Quarter/Year total here forecast "exceed budget by day 8" from three
            // months of spend divided by today's day of the month.
            insight = if (isCurrent) {
                val thisMonth = monthOf(today)
                val monthSpent = allRows.filter { it.date.startsWith(thisMonth) }.sumOf { it.amountValue }
                HomeInsightEngine.compute(allRows, monthSpent, monthBudget, today)
            } else null
        )
    }

    /** Biggest first, with each one's share and its move against the previous period. */
    private fun allocation(
        rows: List<ExpenseEntity>,
        previousRows: List<ExpenseEntity>,
        total: Int
    ): List<CategoryAllocation> {
        val previousByCategory = previousRows.groupBy { it.categoryName }
            .mapValues { (_, group) -> group.sumOf { it.amountInt } }

        return rows.groupBy { it.categoryName }
            .map { (name, group) ->
                val amount = group.sumOf { it.amountInt }
                val before = previousByCategory[name] ?: 0
                CategoryAllocation(
                    name = name,
                    colorHex = group.first().categoryColor,
                    icon = group.first().categoryIcon,
                    amount = amount,
                    sharePercent = if (total <= 0) 0 else ((amount * 100L) / total).toInt(),
                    transactions = group.size,
                    // No baseline, no claim - the badge is simply absent rather
                    // than showing "+100%" against a month with nothing in it.
                    changeVsPreviousPercent =
                        if (before <= 0) null else (((amount - before) * 100L) / before).toInt()
                )
            }
            .sortedByDescending { it.amount }
    }

    /**
     * "Merchant" is the note: this schema has no merchant field, and the note is
     * what the user actually typed for the row. Rows with no note are left out
     * rather than bucketed under an empty name.
     */
    private fun payees(rows: List<ExpenseEntity>, total: Int): List<PayeeTotal> =
        rows.filter { it.note.isNotBlank() }
            .groupBy { it.note.trim() }
            .map { (name, group) ->
                val amount = group.sumOf { it.amountInt }
                PayeeTotal(
                    name = name,
                    categoryName = group.first().categoryName,
                    colorHex = group.first().categoryColor,
                    icon = group.first().categoryIcon,
                    amount = amount,
                    sharePercent = if (total <= 0) 0 else ((amount * 100L) / total).toInt()
                )
            }
            .sortedByDescending { it.amount }
            .take(PAYEE_LIMIT)

    /**
     * Month: the four week-bands of the month. Quarter: its three months.
     * Year: its four quarters - twelve bars do not fit on a phone.
     *
     * A band that has not started yet is filled in from the rate so far and
     * flagged [VelocityBucket.projected], which is what the mockup's dashed
     * last bar means.
     */
    private fun velocity(
        rows: List<ExpenseEntity>,
        months: List<String>,
        scope: AnalyticsScope,
        dailyBurn: Int,
        today: String,
        isCurrent: Boolean
    ): List<VelocityBucket> = when (scope) {
        AnalyticsScope.Month -> {
            val month = months.first()
            val days = daysInMonth(month)
            val todayDay = if (monthOf(today) == month) dayOfMonth(today) else days
            listOf(1 to 7, 8 to 14, 15 to 21, 22 to days).map { (from, to) ->
                val amount = rows.filter { row ->
                    val d = dayOfMonth(row.date)
                    monthOf(row.date) == month && d in from..to
                }.sumOf { it.amountInt }
                val future = isCurrent && from > todayDay
                VelocityBucket(
                    label = "$from-$to",
                    amount = if (future) dailyBurn * (to - from + 1) else amount,
                    projected = future
                )
            }
        }

        AnalyticsScope.Quarter -> months.map { month ->
            VelocityBucket(
                label = monthAbbrev(month),
                amount = rows.filter { monthOf(it.date) == month }.sumOf { it.amountInt },
                projected = false
            )
        }

        AnalyticsScope.Year -> months.chunked(3).map { quarter ->
            VelocityBucket(
                label = monthAbbrev(quarter.first()),
                amount = rows.filter { monthOf(it.date) in quarter }.sumOf { it.amountInt },
                projected = false
            )
        }
    }
}

/* ------------------------------------------------------------------ dates */

internal val ExpenseEntity.amountInt: Int get() = amountValue

/** The months the scope covers, oldest first, **ending** at [anchorMonth]. */
internal fun monthsIn(anchorMonth: String, scope: AnalyticsScope): List<String> {
    val count = when (scope) {
        AnalyticsScope.Month -> 1
        AnalyticsScope.Quarter -> 3
        AnalyticsScope.Year -> 12
    }
    return (count - 1 downTo 0).map { shiftMonthKey(anchorMonth, -it) }
}

/** "2026-10" shifted by whole months, wrapping the year. */
internal fun shiftMonthKey(month: String, delta: Int): String {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return month
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val total = year * 12 + (m - 1) + delta
    return "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
}

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

internal fun monthAbbrev(month: String): String {
    val m = month.split("-").getOrNull(1)?.toIntOrNull() ?: return month
    return MONTH_NAMES.getOrElse(m - 1) { month }
}

private fun periodLabel(anchorMonth: String, scope: AnalyticsScope): String {
    val year = anchorMonth.split("-").firstOrNull().orEmpty()
    return when (scope) {
        AnalyticsScope.Month -> "${monthAbbrev(anchorMonth)} $year"
        AnalyticsScope.Quarter -> {
            val months = monthsIn(anchorMonth, scope)
            "${monthAbbrev(months.first())}–${monthAbbrev(months.last())} $year"
        }
        AnalyticsScope.Year -> {
            val months = monthsIn(anchorMonth, scope)
            val startYear = months.first().split("-").firstOrNull().orEmpty()
            if (startYear == year) year else "$startYear–$year"
        }
    }
}

/** "2026-10-17" -> "17th", for the peak-day caption. */
private fun ordinalDay(iso: String): String {
    val day = dayOfMonth(iso)
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}
