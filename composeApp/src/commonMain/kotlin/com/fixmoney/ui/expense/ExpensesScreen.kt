package com.fixmoney.ui.expense

import com.fixmoney.shared.db.inCurrency

import com.fixmoney.shared.db.amountValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.resources.*
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.plusDaysIso
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.ui.Design
import com.fixmoney.ui.budget.prettyMonthLabel
import com.fixmoney.ui.budget.shiftMonth
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.components.CollapsingScreen
import com.fixmoney.ui.components.ScreenTitleBand
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.designType
import com.fixmoney.ui.isEnabled
import com.fixmoney.ui.home.relativeDay
import com.fixmoney.ui.onboarding.formatIndian
import com.fixmoney.ui.settings.ExportDataDialog
import com.fixmoney.ui.shortCategoryLabel
import kotlin.math.abs
import org.koin.compose.koinInject

/**
 * Expenses, rebuilt to the 2026 mockup
 * (`~/Downloads/stitch_fixmoney_fintech_app_design/fixmoney_expenses/`).
 *
 * Top to bottom: the collapsing app bar, a sticky title row with the month and
 * a Month/Custom scope switch, a summary card (total, month-on-month trend, a
 * stacked allocation bar and per-category chips), search, category filter pills,
 * then the feed grouped by day.
 *
 * **Deliberate differences from the mockup**, all because the data is not there:
 *
 * - Row subtitles read "Groceries & fruits" or the category, never
 *   "Groceries & fruits • 11:15 AM". The `expenses` table stores a `yyyy-MM-dd`
 *   date and no clock time - it is the old app's schema, kept so an update can
 *   read an existing install.
 * - The search field's trailing "tune" button is inert. The mockup implies an
 *   advanced-filter sheet that does not exist; the category pills below are the
 *   only filter there is.
 * - The footer says "N transactions this month", not "All September
 *   transactions reconciled". Nothing in this build reconciles anything, and
 *   claiming it did would be a lie about the user's money.
 */
@Composable
fun ExpensesScreen(
    onOpenExpense: (String) -> Unit = {},
    onAddExpense: () -> Unit = {}
) {
    val colors = Design.colors
    val t = designType()
    val prefs: OnboardingPrefs = koinInject()
    val repo: ExpenseRepository = koinInject()

    val profileCurrency = remember { prefs.getCurrencyCode() }
    val symbol = remember { CurrencyConstants.symbolFor(profileCurrency) }
    val all by repo.observeExpenses().collectAsStateWithLifecycle(emptyList())

    val today = com.fixmoney.ui.components.rememberToday()
    var month by com.fixmoney.ui.components.rememberFollowingMonth(today)
    var monthScope by remember { mutableStateOf(true) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ALL_FILTER) }
    var query by remember { mutableStateOf("") }

    // "Month" narrows to the chosen month; "Custom" is every row the app holds.
    // The mockup's Custom opens a range picker - there is no range state here
    // yet, so it means "all time", which is at least honest about what it shows.
    val inScope = remember(all, month, monthScope) {
        if (monthScope) all.filter { it.date.startsWith(month) } else all
    }
    val previous = remember(all, month) {
        all.filter { it.date.startsWith(shiftMonth(month, -1)) }
    }

    // Totals in the profile's currency only; each row still lists in its own.
    val total = inScope.inCurrency(profileCurrency).sumOf { it.amountInt }
    val previousTotal = previous.inCurrency(profileCurrency).sumOf { it.amountInt }
    val breakdown = remember(inScope) { categoryTotals(inScope.inCurrency(profileCurrency)) }

    val visible = inScope.filter { row ->
        (filter == ALL_FILTER || row.categoryName == filter) &&
            (query.isBlank() ||
                row.note.contains(query, true) ||
                row.categoryName.contains(query, true))
    }
    val grouped = visible.groupBy { it.date }.toList().sortedByDescending { it.first }

    val scroll = rememberScrollState()

    CollapsingScreen(
        // No app bar on this screen: the title row below **is** the header, so
        // there is nothing to collapse and the logo/bell/avatar strip would just
        // repeat what Home already shows.
        title = null,
        scroll = scroll,
        sticky = {
            Column {
                ScreenTitleBand {
                    TitleRow(
                        monthLabel = prettyMonthLabel(month),
                        monthScope = monthScope,
                        onPickMonth = { showMonthPicker = true },
                        onScopeChange = { monthScope = it }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        },
        body = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SummaryCard(
                    symbol = symbol,
                    total = total,
                    previousTotal = previousTotal,
                    monthScope = monthScope,
                    breakdown = breakdown
                )
                SearchField(query = query, onQueryChange = { query = it })
                FilterPills(
                    breakdown = breakdown,
                    selected = filter,
                    onSelect = { filter = it }
                )

                if (grouped.isEmpty()) {
                    EmptyFeed(
                        hasAnyInScope = inScope.isNotEmpty(),
                        onAddExpense = onAddExpense
                    )
                } else {
                    grouped.forEach { (date, rows) ->
                        DayGroup(
                            date = date,
                            rows = rows,
                            symbol = symbol,
                            onOpenExpense = onOpenExpense
                        )
                    }
                    Footer(count = visible.size, onExport = { showExport = true })
                }

                // Clears the bottom bar.
                Spacer(Modifier.height(28.dp))
            }
        }
    )

    if (showMonthPicker) {
        MonthYearDialog(
            title = "Select Month  Year",
            initial = month,
            showDays = false,
            onDismiss = { showMonthPicker = false },
            onConfirm = { month = it; monthScope = true; showMonthPicker = false }
        )
    }
    if (showExport) {
        ExportDataDialog(onDismiss = { showExport = false })
    }
}

/* ------------------------------------------------------------- title row */

@Composable
private fun TitleRow(
    monthLabel: String,
    monthScope: Boolean,
    onPickMonth: () -> Unit,
    onScopeChange: (Boolean) -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Expenses", style = t.headlineLg.copy(fontSize = 22.sp), color = colors.textPrimary)
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(CircleShape)
                .background(colors.surfaceHigh)
                .clickable(onClick = onPickMonth)
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(monthLabel.shortMonthLabel(), style = t.numericSm, color = colors.textMuted)
            Spacer(Modifier.width(2.dp))
            LineIcon(Res.drawable.nd_chevron_down, colors.accent, 16.dp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(colors.surfaceNav).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScopeButton("Month", monthScope) { onScopeChange(true) }
            ScopeButton("Custom", !monthScope) { onScopeChange(false) }
        }
    }
}

@Composable
private fun ScopeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = t.numericSm,
            color = if (selected) colors.surface else colors.textMuted
        )
    }
}

/* ---------------------------------------------------------- summary card */

@Composable
private fun SummaryCard(
    symbol: String,
    total: Int,
    previousTotal: Int,
    monthScope: Boolean,
    breakdown: List<CategoryTotal>
) {
    val colors = Design.colors
    val t = designType()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("TOTAL EXPENSES", style = t.labelCaps, color = colors.textMuted)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$symbol${formatIndian(total)}",
                        style = t.displayHero.copy(fontSize = 30.sp),
                        color = colors.textPrimary
                    )
                    Text(
                        ".00",
                        style = t.headlineSm,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            // Only in Month scope: "vs last month" is meaningless against an
            // all-time total, and showing a number there would be wrong rather
            // than merely unhelpful.
            if (monthScope) TrendBadge(total, previousTotal)
        }

        Spacer(Modifier.height(14.dp))
        AllocationBar(breakdown, total)

        if (breakdown.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                breakdown.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(CircleShape)
                            .background(colors.surfaceHigh)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(categoryTint(entry.colorHex, entry.icon, colors))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            shortCategoryLabel(entry.name),
                            style = t.bodySm,
                            color = colors.textMuted
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "$symbol${formatIndian(entry.amount)}",
                            style = t.numericSm,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendBadge(total: Int, previousTotal: Int) {
    val colors = Design.colors
    val t = designType()
    // No baseline means no claim. The mockup always shows a badge; with no
    // previous month on file there is nothing to compare against.
    if (previousTotal <= 0) return

    val changePercent = ((total - previousTotal) * 100L / previousTotal).toInt()
    val down = changePercent <= 0
    val tint = if (down) colors.positive else colors.negative

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        LineIcon(
            if (down) Res.drawable.nd_trend_down else Res.drawable.nd_trend_up,
            tint,
            14.dp
        )
        Spacer(Modifier.width(3.dp))
        Text("${abs(changePercent)}% vs last month", style = t.numericSm, color = tint)
    }
}

/** The stacked share-of-spend meter under the total. */
@Composable
private fun AllocationBar(breakdown: List<CategoryTotal>, total: Int) {
    val colors = Design.colors
    Row(
        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.surfaceHigh),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (total <= 0) return@Row
        breakdown.forEach { entry ->
            Box(
                Modifier
                    .weight(entry.amount.toFloat().coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(categoryTint(entry.colorHex, entry.icon, colors))
            )
        }
    }
}

/* ---------------------------------------------------------------- search */

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp)
    ) {
        LineIcon(Res.drawable.nd_search, colors.textMuted, 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search merchant, tag, notes...",
                    style = t.bodyMd,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = t.bodyMd.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        // Inert - see this file's doc comment.
        LineIcon(Res.drawable.nd_tune, colors.textMuted, 17.dp)
    }
}

/* ----------------------------------------------------------- filter pills */

@Composable
private fun FilterPills(
    breakdown: List<CategoryTotal>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Built from what the data actually holds, so the row never offers a
        // filter that would return nothing.
        val entries = listOf<CategoryTotal?>(null) + breakdown
        entries.forEach { entry ->
            val label = entry?.name ?: ALL_FILTER
            val isSelected = label == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(if (isSelected) colors.accent else colors.surfaceCard)
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else colors.stroke,
                        CircleShape
                    )
                    .clickable { onSelect(label) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (entry != null) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(categoryTint(entry.colorHex, entry.icon, colors))
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    shortCategoryLabel(label),
                    style = t.numericSm,
                    color = if (isSelected) colors.surface else colors.textMuted
                )
            }
        }
    }
}

/* ------------------------------------------------------------- day groups */

@Composable
private fun DayGroup(
    date: String,
    rows: List<ExpenseEntity>,
    symbol: String,
    onOpenExpense: (String) -> Unit
) {
    val colors = Design.colors
    val t = designType()

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                dayHeading(date),
                style = t.numericSm,
                color = colors.textMuted
            )
            Spacer(Modifier.weight(1f))
            Text("Total: ", style = t.numericSm, color = colors.textMuted)
            Text(
                "$symbol${formatIndian(rows.filter { it.currency.isBlank() || CurrencyConstants.symbolFor(it.currency) == symbol }.sumOf { it.amountInt })}",
                style = t.numericSm,
                color = colors.textPrimary
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))
                }
                ExpenseRow(row, symbol) { onOpenExpense(row.id) }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, symbol: String, onClick: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val tint = categoryTint(expense.categoryColor, expense.categoryIcon, colors)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(categoryGlyph(expense.categoryIcon), tint, 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    expense.note.ifBlank { expense.categoryName },
                    style = t.titleMd,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(CircleShape)
                        .background(tint.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        shortCategoryLabel(expense.categoryName),
                        style = t.labelCaps,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                rowSubtitle(expense),
                style = t.bodySm,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            // The row's own currency: a ₹ expense on a $ profile is still ₹.
            "-${expense.currency.takeIf { it.isNotBlank() }?.let { CurrencyConstants.symbolFor(it) } ?: symbol}${formatIndian(expense.amountInt)}",
            style = t.numericMd.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )
    }
}

/* ---------------------------------------------------------- empty + footer */

@Composable
private fun EmptyFeed(hasAnyInScope: Boolean, onAddExpense: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(vertical = 34.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hasAnyInScope) "Nothing matches that" else "No expenses in this period",
            style = t.titleMd,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasAnyInScope) "Try a different search or category."
            else "Add one and it will show up here.",
            style = t.bodySm,
            color = colors.textMuted
        )
        if (!hasAnyInScope) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f))
                    .clickable(onClick = onAddExpense)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                LineIcon(Res.drawable.nd_plus, colors.accent, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Add expense", style = t.numericSm, color = colors.accent)
            }
        }
    }
}

@Composable
private fun Footer(count: Int, onExport: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(Res.drawable.nd_verified, colors.textMuted, 17.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (count == 1) "1 transaction in this period" else "$count transactions in this period",
            style = t.bodySm,
            color = colors.textMuted
        )
        if (isEnabled(FeatureFlag.ExportData)) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onExport)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Export expense statement", style = t.numericSm, color = colors.accent)
                Spacer(Modifier.width(4.dp))
                LineIcon(Res.drawable.nd_arrow_outward, colors.accent, 14.dp)
            }
        }
    }
}

/* ----------------------------------------------------------------- shared */

private const val ALL_FILTER = "All"

/** The amount column is a string in the old schema; everything here wants an Int. */
internal val ExpenseEntity.amountInt: Int get() = amountValue

/** One category's share of the period, biggest first. */
internal data class CategoryTotal(
    val name: String,
    val colorHex: String,
    val icon: String,
    val amount: Int
)

internal fun categoryTotals(rows: List<ExpenseEntity>): List<CategoryTotal> =
    rows.groupBy { it.categoryName }
        .map { (name, group) ->
            CategoryTotal(
                name = name,
                colorHex = group.first().categoryColor,
                icon = group.first().categoryIcon,
                amount = group.sumOf { it.amountInt }
            )
        }
        .sortedByDescending { it.amount }

/** "TODAY, 3 OCT" / "YESTERDAY, 2 OCT" / "28 SEP 2026", as the mockup heads each group. */
private fun dayHeading(iso: String): String {
    val today = todayIso()
    val pretty = relativeDay(iso)
    val parts = iso.split("-")
    val year = parts.getOrNull(0).orEmpty()
    return when (iso) {
        today -> "TODAY, ${shortDate(iso)}".uppercase()
        plusDaysIso(today, -1) -> "YESTERDAY, ${shortDate(iso)}".uppercase()
        else -> if (pretty == iso) iso else "${shortDate(iso)} $year".uppercase()
    }
}

private fun shortDate(iso: String): String {
    val parts = iso.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return iso
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return iso
    val names = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "$day ${names.getOrElse(month - 1) { "" }}"
}

/** "September 2026" -> "Sep 2026", which is what fits the chip. */
private fun String.shortMonthLabel(): String {
    val parts = split(" ")
    if (parts.size != 2) return this
    return "${parts[0].take(3)} ${parts[1]}"
}

/**
 * The mockup's "Flat White & Croissant • 4:15 PM". There is no clock time in the
 * schema, so this is the note and the day - and when the note is already the
 * row's title, just the day.
 */
private fun rowSubtitle(expense: ExpenseEntity): String {
    val day = relativeDay(expense.date)
    return if (expense.note.isBlank()) day else "${expense.categoryName} • $day"
}
