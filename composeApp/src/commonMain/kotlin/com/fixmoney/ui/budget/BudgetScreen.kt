package com.fixmoney.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.resources.*
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.expense.BudgetProgress
import com.fixmoney.shared.expense.BudgetRepository
import com.fixmoney.shared.expense.BudgetStateFactory
import com.fixmoney.shared.expense.BudgetUiState
import com.fixmoney.shared.expense.EnvelopeHealth
import com.fixmoney.shared.expense.SpendingGuard
import com.fixmoney.shared.expense.health
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.ui.Design
import com.fixmoney.ui.DesignColors
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.components.CollapsingScreen
import com.fixmoney.ui.components.ScreenTitleBand
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.formatIndian
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Budget, rebuilt to the 2026 mockup
 * (`~/Downloads/stitch_fixmoney_fintech_app_design/fixmoney_budget_limits/`).
 *
 * The overall cap, the pace readout, the spending guard and every envelope are
 * computed by `BudgetStateFactory` from the budget rows and the month's
 * expenses - the same sources Home and Analytics read.
 *
 * **Three parts of the mockup are deliberately absent**, because shipping a
 * control that silently does nothing is worse than not shipping it:
 *
 * - The **Category Envelopes / Smart Automation / Rollover tabs**. Two of the
 *   three have nothing behind them.
 * - The **Budget Automation card** ("Rollover Unspent Balances", "Velocity
 *   Shield"). Neither rule exists; both switches would be decoration.
 * - The guard card's **Dismiss** button. There is nowhere to persist a
 *   dismissal, so it would come back on the next recomposition.
 *
 * Add them back together with the features, not before.
 */
@Composable
fun BudgetScreen() {
    val colors = Design.colors
    val t = designType()
    val factory: BudgetStateFactory = koinInject()
    val repository: BudgetRepository = koinInject()
    val categoryRepo: CategoryRepository = koinInject()
    val prefs: OnboardingPrefs = koinInject()
    val scope = rememberCoroutineScope()

    var month by remember { mutableStateOf(currentMonthApi()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    LaunchedEffect(Unit) { categories = categoryRepo.getCategories() }

    val flow = remember(month) { factory.observe(month) }
    val state by flow.collectAsStateWithLifecycle(remember(month) { factory.build(month = month) })

    val scroll = rememberScrollState()

    CollapsingScreen(
        title = null,
        scroll = scroll,
        sticky = {
            Column {
                ScreenTitleBand {
                    Text(
                        "Budget",
                        style = t.headlineLg.copy(fontSize = 22.sp),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(CircleShape)
                            .background(colors.surfaceHigh)
                            .clickable { showMonthPicker = true }
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(prettyMonthLabel(month).shortened(), style = t.numericSm, color = colors.accent)
                        Spacer(Modifier.width(3.dp))
                        LineIcon(Res.drawable.nd_chevron_down, colors.textMuted, 15.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        },
        body = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!state.hasCaps) {
                    EmptyBudget { editing = EditTarget(null) }
                } else {
                    OverallCapCard(state)
                    state.guard?.let { guard ->
                        GuardCard(guard, state.currencySymbol) {
                            editing = EditTarget(
                                state.envelopes.firstOrNull { it.categoryName == guard.categoryName }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Category envelopes", style = t.headlineSm, color = colors.textPrimary)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(CircleShape).background(colors.surfaceHigh)
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text("${state.envelopes.size} active", style = t.labelCaps, color = colors.textMuted)
                        }
                    }

                    state.envelopes.forEach { envelope ->
                        EnvelopeCard(envelope, state) { editing = EditTarget(envelope) }
                    }

                    AddLimitButton { editing = EditTarget(null) }
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
            onConfirm = { month = it; showMonthPicker = false }
        )
    }

    editing?.let { target ->
        EditBudgetSheet(
            existing = target.envelope,
            categories = categories,
            takenCategoryIds = state.envelopes.map { it.categoryId }.toSet(),
            currencySymbol = state.currencySymbol,
            daysLeft = state.daysLeft,
            onDismiss = { editing = null },
            onSave = { category, limit ->
                scope.launch {
                    repository.setBudget(category, limit, month, prefs.getCurrencyCode())
                    editing = null
                }
            },
            onRemove = { id ->
                scope.launch {
                    repository.delete(id)
                    editing = null
                }
            }
        )
    }
}

/** Null [envelope] means "add a new limit" rather than edit an existing one. */
private data class EditTarget(val envelope: BudgetProgress?)

/* ------------------------------------------------------- overall cap card */

@Composable
private fun OverallCapCard(state: BudgetUiState) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("OVERALL MONTHLY CAP", style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            val tone = if (state.onPace) colors.positive else colors.negative
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(tone.copy(alpha = 0.15f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${if (state.onPace) "ON PACE" else "OVER PACE"} • ${state.percentUsed}%",
                    style = t.labelCaps,
                    color = tone
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.currencySymbol}${formatIndian(state.totalSpent)}",
                style = t.displayHero.copy(fontSize = 30.sp),
                color = colors.textPrimary
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "/ ${state.currencySymbol}${formatIndian(state.totalCap)}",
                style = t.numericSm,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(colors.surfaceHigh),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            state.envelopes.filter { it.spent > 0 }.forEach { envelope ->
                Box(
                    Modifier
                        .weight(envelope.spent.toFloat().coerceAtLeast(0.001f))
                        .fillMaxHeight().clip(CircleShape)
                        .background(categoryTint(envelope.categoryColor, envelope.categoryIcon, colors))
                )
            }
            // Keeps the track visible when the caps are barely touched.
            val unspent = (state.remaining).coerceAtLeast(0)
            if (unspent > 0) Spacer(Modifier.weight(unspent.toFloat()))
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.currencySymbol}${formatIndian(state.totalSpent)} spent",
                style = t.bodySm,
                color = colors.textMuted
            )
            Spacer(Modifier.weight(1f))
            LineIcon(Res.drawable.nd_calendar, colors.textMuted, 13.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                if (state.daysLeft == 1) "1 day left in cycle" else "${state.daysLeft} days left in cycle",
                style = t.bodySm,
                color = colors.textMuted
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(colors.surface).padding(12.dp)
        ) {
            CapStat(
                "REMAINING",
                "${state.currencySymbol}${formatIndian(state.remaining.coerceAtLeast(0))}",
                if (state.remaining >= 0) colors.positive else colors.negative,
                Modifier.weight(1f)
            )
            CapStat(
                "DAILY BURN",
                "${state.currencySymbol}${formatIndian(state.dailyBurn)}/d",
                colors.textPrimary,
                Modifier.weight(1f)
            )
            CapStat(
                "PACE DELTA",
                "${if (state.paceDelta > 0) "+" else ""}${state.paceDelta}%",
                if (state.paceDelta <= 0) colors.positive else colors.negative,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CapStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = Design.colors
    val t = designType()
    Column(modifier) {
        Text(label, style = t.labelCaps, color = colors.textMuted, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, style = t.numericSm, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* -------------------------------------------------------------- guard card */

@Composable
private fun GuardCard(guard: SpendingGuard, symbol: String, onAdjust: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.strokeAccent, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LineIcon(Res.drawable.nd_sparkle, colors.accent, 17.dp)
            Spacer(Modifier.width(7.dp))
            Text("FIXOO SPENDING GUARD", style = t.labelCaps, color = colors.accent)
            Spacer(Modifier.weight(1f))
            Text("Real-time", style = t.labelCaps, color = colors.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        Text(guardText(guard, symbol), style = t.bodyMd, color = colors.textPrimary)

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.15f))
                .clickable(onClick = onAdjust)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Adjust ${guard.categoryName} cap", style = t.numericSm, color = colors.accent)
            Spacer(Modifier.width(5.dp))
            LineIcon(Res.drawable.nd_arrow_right, colors.accent, 15.dp)
        }
    }
}

private fun guardText(guard: SpendingGuard, symbol: String): String = when {
    guard.isOver ->
        "${guard.categoryName} is ${symbol}${formatIndian(guard.overspend)} over its cap with " +
            "${guard.daysLeft} days left. Raising the cap or easing off here keeps the month's total intact."
    else ->
        "${guard.categoryName} has used ${guard.percentUsed}% of its cap with ${guard.daysLeft} days " +
            "left. It is the envelope most likely to run out first."
}

/* ----------------------------------------------------------- envelope card */

@Composable
private fun EnvelopeCard(envelope: BudgetProgress, state: BudgetUiState, onEdit: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val tint = categoryTint(envelope.categoryColor, envelope.categoryIcon, colors)
    val health = envelope.health()
    val healthTint = health.tint(colors)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onEdit)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(categoryGlyph(envelope.categoryIcon), tint, 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    envelope.categoryName,
                    style = t.titleMd,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${state.currencySymbol}${formatIndian(envelope.spent)} of " +
                        "${state.currencySymbol}${formatIndian(envelope.limit)} cap",
                    style = t.bodySm,
                    color = colors.textMuted,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${envelope.rawPercent}%",
                    style = t.numericMd.copy(fontWeight = FontWeight.Bold),
                    color = healthTint
                )
                Text(health.label, style = t.labelCaps, color = healthTint)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(colors.surfaceHigh)) {
            Box(
                Modifier.fillMaxWidth(envelope.percent / 100f)
                    .fillMaxHeight().clip(CircleShape).background(healthTint)
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (envelope.isOver) {
                    "${state.currencySymbol}${formatIndian(-envelope.remaining)} over"
                } else {
                    "${state.currencySymbol}${formatIndian(envelope.remaining)} left"
                },
                style = t.bodySm,
                color = if (envelope.isOver) colors.negative else colors.textMuted
            )
            Spacer(Modifier.weight(1f))
            // What the remainder allows per day for the rest of the month. The
            // mockup's right-hand captions are a mix of this, bill due dates and
            // subscription dates; only the runway is computable here.
            if (!envelope.isOver && state.daysLeft > 0) {
                Text(
                    "~${state.currencySymbol}${formatIndian(envelope.remaining / state.daysLeft)}/day runway",
                    style = t.bodySm,
                    color = colors.textMuted
                )
            }
        }
    }
}

private val EnvelopeHealth.label: String
    get() = when (this) {
        EnvelopeHealth.Safe -> "SAFE"
        EnvelopeHealth.Healthy -> "HEALTHY"
        EnvelopeHealth.Watch -> "WATCH"
        EnvelopeHealth.Critical -> "CRITICAL"
        EnvelopeHealth.Over -> "OVER"
    }

private fun EnvelopeHealth.tint(colors: DesignColors): Color = when (this) {
    EnvelopeHealth.Safe, EnvelopeHealth.Healthy -> colors.positive
    EnvelopeHealth.Watch -> colors.categoryEnt
    EnvelopeHealth.Critical, EnvelopeHealth.Over -> colors.negative
}

/* ----------------------------------------------------------------- shared */

@Composable
private fun AddLimitButton(onClick: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        LineIcon(Res.drawable.nd_plus, colors.accent, 17.dp)
        Spacer(Modifier.width(8.dp))
        Text("Add category limit", style = t.numericSm, color = colors.accent)
    }
}

@Composable
private fun EmptyBudget(onAdd: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No caps for this month", style = t.titleMd, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Set a limit per category and this becomes your envelope board.",
            style = t.bodySm,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.15f))
                .clickable(onClick = onAdd)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            LineIcon(Res.drawable.nd_plus, colors.accent, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Add category limit", style = t.numericSm, color = colors.accent)
        }
    }
}

/** "September 2026" -> "Sep 2026", which is what fits the chip. */
internal fun String.shortened(): String {
    val parts = split(" ")
    return if (parts.size == 2) "${parts[0].take(3)} ${parts[1]}" else this
}

/** "2026-09" -> "September 2026". */
internal fun prettyMonthLabel(month: String): String {
    val parts = month.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val names = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    return "${names.getOrElse(m - 1) { month }} ${parts[0]}"
}

/** "2026-09" shifted by whole months, wrapping the year. */
internal fun shiftMonth(month: String, delta: Int): String {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return month
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val total = year * 12 + (m - 1) + delta
    return "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
}
