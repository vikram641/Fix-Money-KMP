package com.fixmoney.shared.home

import com.fixmoney.shared.db.BudgetDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentHour
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.dayOfMonth
import com.fixmoney.shared.platform.daysInMonth
import com.fixmoney.shared.platform.monthOf
import com.fixmoney.shared.platform.plusDaysIso
import com.fixmoney.shared.platform.todayIso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** What the Home screen shows, all of it derived from Room. */
/** One slice of Home's donut: a category, its share of the month's spend. */
data class CategorySlice(
    val name: String,
    val colorHex: String,
    val amount: Int,
    val percent: Int
)

/** One row of Home's "Recent" list. */
data class RecentExpense(
    val id: String,
    val title: String,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val dateIso: String,
    val amount: Int
)

data class HomeUiState(
    val greeting: String,
    val initials: String,
    val month: String,
    val currencySymbol: String,
    val totalSpent: Int,
    val weekSpent: Int,
    val totalBudget: Int,
    val breakdown: List<CategorySlice> = emptyList(),
    val insight: HomeInsight? = null,
    /** Days remaining in the month the screen is showing, 0 for a past month. */
    val daysLeft: Int = 0,
    /** Spend so far divided by days elapsed - the hero card's "Daily avg". */
    val dailyAverage: Int = 0,
    val recent: List<RecentExpense> = emptyList()
) {
    val remaining: Int get() = (totalBudget - totalSpent).coerceAtLeast(0)

    /** Same integer percentage the old `Utils.calculatePercentage` produced. */
    val percentUsed: Int
        get() = if (totalBudget <= 0) 0 else ((totalSpent * 100L) / totalBudget).toInt().coerceIn(0, 100)

    val hasExpenses: Boolean get() = totalSpent > 0

    /**
     * The hero card's status word.
     *
     * "On track" allows [PACE_GRACE] points of overshoot against how far into
     * the month we are. A strict comparison is too twitchy to be useful: one
     * ordinary purchase on the 2nd puts any month "running high", and the
     * mockup itself shows "On track" at 50% spent with 18 of 30 days left,
     * which a strict rule would have called high.
     */
    val paceLabel: String
        get() = when {
            totalBudget <= 0 -> "No budget set"
            totalSpent > totalBudget -> "Over budget"
            daysLeft <= 0 -> "Month closed"
            isOnTrack -> "On track"
            else -> "Running high"
        }

    val isOnTrack: Boolean
        get() = totalBudget > 0 && totalSpent <= totalBudget &&
            percentUsed <= monthElapsedPercent + PACE_GRACE

    /** How far through the month we are, as a percentage. */
    var monthElapsedPercent: Int = 100
        internal set
}

private const val RECENT_LIMIT = 4

/** How far ahead of the month's pace counts as still "on track", in points. */
private const val PACE_GRACE = 10

/**
 * How far before the selected month Home reads.
 *
 * The insight baseline ends a week before today and runs three weeks back, so
 * on the 1st of a month it reaches 27 days into the previous one. 45 leaves
 * room for that plus the slack a month boundary adds, without unbounding the
 * query.
 */
private const val LOOKBACK_DAYS = 45

class HomeStateFactory(
    private val prefs: OnboardingPrefs,
    private val expenses: ExpenseRepository,
    private val budgets: BudgetDao
) {

    /**
     * Re-emits whenever the expenses **or budgets** table changes, so both a new
     * expense and a budget set on the Budget screen show up on Home without a
     * manual refresh.
     *
     * The budgets half is why this is a `combine` and not a plain `map`: Home
     * used to read the monthly figure straight off the preference, so setting a
     * category budget moved the Budget screen's roll-up and left Home's
     * "Remaining" untouched. The two now come from one source.
     */
    fun observe(month: String = currentMonthApi()): Flow<HomeUiState> =
        combine(
            // A window, not the month and not the whole table.
            //
            // Month-only was the original bug: the insight baseline is the three
            // weeks ending a week ago, so in the first days of a month it sits
            // entirely in the previous one and the engine silently compared this
            // week against nothing. Reading the table whole fixed that, but made
            // every insert re-read and re-derive every row a user has ever
            // entered.
            //
            // [LOOKBACK_DAYS] reaches far enough back to cover the baseline from
            // the first of the month, and `date` is indexed, so the cost is tied
            // to the window rather than to the lifetime of the ledger.
            expenses.observeExpensesBetween(
                from = plusDaysIso("$month-01", -LOOKBACK_DAYS),
                to = "$month-${daysInMonth(month).toString().padStart(2, '0')}"
            ),
            budgets.observeForMonth(month)
        ) { windowRows, budgetRows ->
            build(windowRows, month, budgetRows.sumOf { it.limitAmount })
        }

    fun build(
        allRows: List<ExpenseEntity> = emptyList(),
        month: String = currentMonthApi(),
        budgetTotal: Int = 0
    ): HomeUiState {
        val name = prefs.getUserName()
        val rows = allRows.filter { it.date.startsWith(month) }
        val spent = rows.sumOf { it.amount.toIntOrNull() ?: 0 }

        // The last 7 days including today, which is what the old Home's "This
        // Week" tile meant. It read 0 until now only because there was no date
        // arithmetic to express the window - see `platform/DateMath.kt`.
        val today = todayIso()
        val week = if (monthOf(today) == month) {
            rows.inRange(plusDaysIso(today, -6), today).sumOf { it.amount.toIntOrNull() ?: 0 }
        } else {
            // Looking at another month: "this week" has no meaning there, and
            // showing the current week's figure beside that month's total would
            // be plainly wrong.
            0
        }

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
        val budget = if (budgetTotal > 0) budgetTotal else prefs.getMonthlyBudget()

        // Only meaningful for the month in progress. Looking at a past month,
        // "18 days left" and a daily average extrapolated from today would both
        // be nonsense, so they read 0 and the card says "Month closed".
        val isCurrentMonth = monthOf(today) == month
        val totalDays = daysInMonth(month)
        val elapsed = if (isCurrentMonth) dayOfMonth(today) else totalDays
        val left = (totalDays - elapsed).coerceAtLeast(0)
        val perDay = if (elapsed > 0) spent / elapsed else 0

        val recent = rows
            .sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.localCreatedAt })
            .take(RECENT_LIMIT)
            .map { row ->
                RecentExpense(
                    id = row.id,
                    // The note is what the user actually typed, so it is the
                    // better title; the category stands in when there is none.
                    title = row.note.ifBlank { row.categoryName },
                    categoryName = row.categoryName,
                    categoryColor = row.categoryColor,
                    categoryIcon = row.categoryIcon,
                    dateIso = row.date,
                    amount = row.amount.toIntOrNull() ?: 0
                )
            }

        return HomeUiState(
            greeting = "${greetingForHour()}\n${name?.takeIf { it.isNotBlank() } ?: "there"} 👋",
            initials = initialsFrom(name),
            month = month,
            currencySymbol = CurrencyConstants.symbolFor(prefs.getCurrencyCode()),
            totalSpent = spent,
            weekSpent = week,
            // Budget rows win when there are any; the onboarding preference is
            // the fallback for an install that has none (one that onboarded
            // before the rows were being written, or a month never budgeted).
            totalBudget = budget,
            breakdown = slices,
            // Only for the month actually in progress - a projection about a
            // month that has already ended is noise.
            insight = if (isCurrentMonth) {
                HomeInsightEngine.compute(allRows, spent, budget, today)
            } else null,
            daysLeft = if (isCurrentMonth) left else 0,
            dailyAverage = perDay,
            recent = recent
        ).also { it.monthElapsedPercent = (elapsed * 100) / totalDays }
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
