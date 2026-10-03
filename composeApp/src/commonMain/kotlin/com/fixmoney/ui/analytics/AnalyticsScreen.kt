package com.fixmoney.ui.analytics

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.resources.*
import com.fixmoney.shared.analytics.AnalyticsScope
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.analytics.AnalyticsStateFactory
import com.fixmoney.shared.analytics.AnalyticsUiState
import com.fixmoney.shared.analytics.CategoryAllocation
import com.fixmoney.shared.analytics.PayeeTotal
import com.fixmoney.shared.analytics.VelocityBucket
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.ui.Design
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.components.CollapsingScreen
import com.fixmoney.ui.components.ScreenTitleBand
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.designType
import com.fixmoney.ui.isEnabled
import com.fixmoney.ui.home.NewDonut
import com.fixmoney.ui.home.compactAmount
import com.fixmoney.ui.onboarding.formatIndian
import com.fixmoney.shared.home.CategorySlice
import kotlin.math.abs
import org.koin.compose.koinInject

/**
 * Analytics, rebuilt to the 2026 mockup
 * (`~/Downloads/stitch_fixmoney_fintech_app_design/fixmoney_analytics/`).
 *
 * Every figure is computed from the expense and budget tables by
 * `AnalyticsStateFactory` - no API call, nothing cached - so the screen can
 * never disagree with Expenses or Home.
 *
 * **Deliberate differences from the mockup:**
 *
 * - **"Merchant Concentration" is grouped by the note.** This schema has no
 *   merchant field; the note is what the user actually typed for the row. Rows
 *   with no note are left out rather than bucketed under an empty name, and the
 *   whole card is hidden when nothing has one.
 * - The Copilot card's text comes from `HomeInsightEngine`, which is arithmetic
 *   over local rows. The mockup's "weekend dining accounted for 64%" implies a
 *   weekday/weekend split of a single category that the engine does not compute.
 * - Per-category badges compare against the **previous period**, not a rolling
 *   average, and are absent when there is no previous period to compare with.
 * - **Year shows four quarterly bars, not twelve monthly ones** - twelve do not
 *   fit across a phone.
 */
@Composable
fun AnalyticsScreen(onAskAi: () -> Unit = {}) {
    val colors = Design.colors
    val t = designType()
    val factory: AnalyticsStateFactory = koinInject()

    var month by remember { mutableStateOf(currentMonthApi()) }
    var scope by remember { mutableStateOf(AnalyticsScope.Month) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val flow = remember(month, scope) { factory.observe(month, scope) }
    val state by flow.collectAsStateWithLifecycle(
        remember(month, scope) { factory.build(anchorMonth = month, scope = scope) }
    )

    val scroll = rememberScrollState()

    CollapsingScreen(
        // No app bar: the title row below is this screen's header.
        title = null,
        scroll = scroll,
        sticky = {
            Column {
                ScreenTitleBand {
                    Text(
                        "Analytics",
                        style = t.headlineLg.copy(fontSize = 22.sp),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(CircleShape)
                            .background(colors.surfaceHigh)
                            .clickable { showMonthPicker = true }
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(state.periodLabel, style = t.numericSm, color = colors.accent)
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
                ScopeTabs(scope) { scope = it }

                if (!state.hasData) {
                    EmptyAnalytics()
                } else {
                    OutflowCard(state)
                    VelocityCard(state)
                    AllocationCard(state)
                    if (isEnabled(FeatureFlag.AiInsights)) {
                        state.insight?.let { CopilotCard(state, onAskAi) }
                    }
                    if (state.payees.isNotEmpty()) PayeeCard(state)
                }

                Spacer(Modifier.height(92.dp))
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
}

/* ------------------------------------------------------------- scope tabs */

@Composable
private fun ScopeTabs(selected: AnalyticsScope, onSelect: (AnalyticsScope) -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceNav).padding(4.dp)
    ) {
        AnalyticsScope.entries.forEach { option ->
            val active = option == selected
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (active) colors.surfaceHigh else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.name,
                    style = t.numericSm,
                    color = if (active) colors.accent else colors.textMuted
                )
            }
        }
    }
}

/* ----------------------------------------------------------- outflow card */

@Composable
private fun OutflowCard(state: AnalyticsUiState) {
    val colors = Design.colors
    val t = designType()
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TOTAL OUTFLOW", style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            TrendBadge(state.total, state.previousTotal)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                state.currencySymbol,
                style = t.headlineSm,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${formatIndian(state.total)}.00",
                style = t.displayHero.copy(fontSize = 30.sp),
                color = colors.textPrimary
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(12.dp)
        ) {
            MiniStat("DAILY BURN", "${state.currencySymbol}${formatIndian(state.dailyBurn)}/d", Modifier.weight(1f))
            MiniStat(
                "PEAK DAY",
                "${state.currencySymbol}${formatIndian(state.peakDayAmount)}",
                Modifier.weight(1f),
                caption = state.peakDayLabel.takeIf { it.isNotBlank() }?.let { "($it)" }
            )
            MiniStat(
                "PROJECTED",
                "${state.currencySymbol}${formatIndian(state.projected)}",
                Modifier.weight(1f),
                tint = colors.accent
            )
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    tint: Color? = null
) {
    val colors = Design.colors
    val t = designType()
    Column(modifier) {
        Text(label, style = t.labelCaps, color = colors.textMuted, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = t.numericSm,
                color = tint ?: colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            caption?.let {
                Spacer(Modifier.width(3.dp))
                Text(it, style = t.bodySm, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TrendBadge(total: Int, previousTotal: Int) {
    val colors = Design.colors
    val t = designType()
    // No baseline, no claim.
    if (previousTotal <= 0) return

    val change = ((total - previousTotal) * 100L / previousTotal).toInt()
    val down = change <= 0
    val tint = if (down) colors.positive else colors.negative
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        LineIcon(
            if (down) Res.drawable.nd_trend_down else Res.drawable.nd_trend_up,
            tint,
            13.dp
        )
        Spacer(Modifier.width(3.dp))
        Text("${abs(change)}% vs prev", style = t.numericSm, color = tint)
    }
}

/* ---------------------------------------------------------- velocity card */

@Composable
private fun VelocityCard(state: AnalyticsUiState) {
    val colors = Design.colors
    val t = designType()
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spending Velocity", style = t.titleMd, color = colors.textPrimary)
                Text(velocitySubtitle(state.scope), style = t.bodySm, color = colors.textMuted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(colors.surfaceHigh)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
                Spacer(Modifier.width(5.dp))
                Text(
                    "RUN RATE ${state.currencySymbol}${formatIndian(state.dailyBurn)}/D",
                    style = t.labelCaps,
                    color = colors.textMuted
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        VelocityChart(state)
    }
}

private fun velocitySubtitle(scope: AnalyticsScope) = when (scope) {
    AnalyticsScope.Month -> "4-week disbursement pace"
    AnalyticsScope.Quarter -> "Month-by-month pace"
    AnalyticsScope.Year -> "Quarter-by-quarter pace"
}

@Composable
private fun VelocityChart(state: AnalyticsUiState) {
    val colors = Design.colors
    val t = designType()
    val buckets = state.velocity
    if (buckets.isEmpty()) return

    val tallestBar = maxOf(buckets.maxOf { it.amount }, 1)

    // **The budget only joins the scale when it is close enough to matter.**
    // Scaling to it unconditionally was the first attempt and it destroyed the
    // chart: a budget several times the spend (easy to produce - it is the sum
    // of every category's limit) pushed every real bar down to a 2% sliver.
    // Within 1.25x of the tallest bar the rule is the useful "am I near it?"
    // line the mockup draws; beyond that it is off the top and the bars are
    // what the user came to read.
    val showBudgetRule = state.budgetLimit in 1..(tallestBar * 5 / 4)
    val peak = if (showBudgetRule) maxOf(tallestBar, state.budgetLimit) else tallestBar

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
          if (showBudgetRule) {
            // A dashed rule at the limit's own height, as the mockup has it.
            val fraction = state.budgetLimit.toFloat() / peak
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomStart)
                    .padding(bottom = (150.dp * fraction).coerceAtMost(149.dp))
            ) {
                Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(
                        color = colors.textMuted,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = size.height,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }
            }
          }
          Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
          ) {
            buckets.forEach { bucket ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        compactAmount("", bucket.amount).let {
                            if (bucket.projected) "~$it" else it
                        },
                        style = t.labelCaps,
                        color = if (bucket.projected) colors.textMuted else colors.textPrimary,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    VelocityBar(
                        bucket = bucket,
                        fraction = bucket.amount.toFloat() / peak,
                        isTallest = bucket.amount == buckets.maxOf { it.amount } && !bucket.projected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
          }
        }

        if (state.budgetLimit > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Budget limit ${state.currencySymbol}${formatIndian(state.budgetLimit)}",
                style = t.labelCaps,
                color = colors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            buckets.forEach { bucket ->
                Text(
                    bucket.label,
                    style = t.labelCaps,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VelocityBar(
    bucket: VelocityBucket,
    fraction: Float,
    isTallest: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = Design.colors
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        val height = (fraction.coerceIn(0.02f, 1f))
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(height)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (bucket.projected) {
                        // A projection is drawn as an outline, never as a solid
                        // bar: it is an extrapolation, and it must not read as
                        // money that has actually been spent.
                        Modifier
                            .background(colors.surfaceHigh.copy(alpha = 0.45f))
                            .border(1.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    } else if (isTallest) {
                        Modifier.background(
                            Brush.verticalGradient(listOf(colors.fabStart, colors.fabEnd))
                        )
                    } else {
                        Modifier.background(colors.surfaceHigh)
                    }
                )
        )
    }
}

/* -------------------------------------------------------- allocation card */

@Composable
private fun AllocationCard(state: AnalyticsUiState) {
    val colors = Design.colors
    val t = designType()
    val top = state.topCategory

    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Category Allocation", style = t.titleMd, color = colors.textPrimary)
                Text(
                    "${state.allocation.size} active categories",
                    style = t.bodySm,
                    color = colors.textMuted
                )
            }
            Box(
                Modifier.clip(CircleShape).background(colors.surface)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(state.periodLabel, style = t.labelCaps, color = colors.textMuted)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NewDonut(
                slices = state.allocation.take(5).map {
                    CategorySlice(it.name, it.colorHex, it.amount, it.sharePercent)
                },
                centerLabel = "${state.topSharePercent}%",
                size = 86.dp,
                stroke = 12.dp,
                centerCaption = "TOP"
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Primary load", style = t.numericSm, color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        top?.name.orEmpty(),
                        style = t.numericSm,
                        color = top?.let { categoryTint(it.colorHex, it.icon, colors) }
                            ?: colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Takes ${state.currencySymbol}${state.topCategoryPerThousand} of every " +
                        "${state.currencySymbol}1,000 spent this period.",
                    style = t.bodySm,
                    color = colors.textMuted
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        .background(colors.surfaceHigh),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.allocation.forEach { entry ->
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
        }

        Spacer(Modifier.height(6.dp))
        state.allocation.forEachIndexed { index, entry ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))
            }
            AllocationRow(entry, state.currencySymbol)
        }
    }
}

@Composable
private fun AllocationRow(entry: CategoryAllocation, symbol: String) {
    val colors = Design.colors
    val t = designType()
    val tint = categoryTint(entry.colorHex, entry.icon, colors)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(categoryGlyph(entry.icon), tint, 19.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = t.titleMd,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.sharePercent}% share", style = t.bodySm, color = colors.textMuted)
                entry.changeVsPreviousPercent?.let { change ->
                    Spacer(Modifier.width(6.dp))
                    val up = change > 0
                    val tone = if (up) colors.negative else colors.positive
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(tone.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "${if (up) "+" else ""}$change% vs prev",
                            style = t.labelCaps,
                            color = tone,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "-$symbol${formatIndian(entry.amount)}",
                style = t.numericMd.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1
            )
            Text(
                if (entry.transactions == 1) "1 transaction" else "${entry.transactions} transactions",
                style = t.labelCaps,
                color = colors.textMuted,
                maxLines = 1
            )
        }
    }
}

/* ----------------------------------------------------------- copilot card */

@Composable
private fun CopilotCard(state: AnalyticsUiState, onAskAi: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val insight = state.insight ?: return

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.strokeAccent, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Res.drawable.nd_sparkle, colors.accent, 18.dp)
            Spacer(Modifier.width(7.dp))
            Text("FIXOO COPILOT INSIGHT", style = t.labelCaps, color = colors.accent)
        }
        Spacer(Modifier.height(12.dp))
        Text(copilotText(state), style = t.bodyMd, color = colors.textPrimary)

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f))
                    .clickable(onClick = onAskAi)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Deep dive with Fixoo", style = t.numericSm, color = colors.accent)
                Spacer(Modifier.width(5.dp))
                LineIcon(Res.drawable.nd_arrow_right, colors.accent, 15.dp)
            }
            Spacer(Modifier.weight(1f))
            Text("Calculated today", style = t.labelCaps, color = colors.textMuted)
        }
    }
}

private fun copilotText(state: AnalyticsUiState): String {
    val insight = state.insight ?: return ""
    val category = insight.categoryName
    val change = insight.categoryPercentChange
    val overageDay = insight.projectedOverageDay

    return when {
        category != null && change != null && overageDay != null ->
            "$category is up $change% on your 3-week average, and at this pace the " +
                "budget runs out around day $overageDay of the month."
        category != null && change != null ->
            "$category is up $change% on your 3-week average — it is the line moving " +
                "fastest this period."
        overageDay != null ->
            "At the current rate you reach the budget around day $overageDay of the month."
        else -> "Nothing unusual in this period's spending."
    }
}

/* ------------------------------------------------------------- payee card */

@Composable
private fun PayeeCard(state: AnalyticsUiState) {
    val colors = Design.colors
    val t = designType()
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Where it went", style = t.titleMd, color = colors.textPrimary)
                Text("Top ${state.payees.size} by volume", style = t.bodySm, color = colors.textMuted)
            }
            Text("${state.payeeShareOfTotal}% OF TOTAL", style = t.labelCaps, color = colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        state.payees.forEachIndexed { index, payee ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            PayeeRow(payee, state.currencySymbol)
        }
    }
}

@Composable
private fun PayeeRow(payee: PayeeTotal, symbol: String) {
    val colors = Design.colors
    val t = designType()
    val tint = categoryTint(payee.colorHex, payee.icon, colors)

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                payee.name,
                style = t.titleMd,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(payee.categoryName, style = t.labelCaps, color = colors.textMuted, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "$symbol${formatIndian(payee.amount)}",
                style = t.numericMd.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(colors.surfaceHigh)) {
            Box(
                Modifier.fillMaxWidth((payee.sharePercent / 100f).coerceIn(0.01f, 1f))
                    .fillMaxHeight().clip(CircleShape).background(tint)
            )
        }
    }
}

/* ----------------------------------------------------------------- shared */

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val colors = Design.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun EmptyAnalytics() {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nothing to analyse yet", style = t.titleMd, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add expenses in this period and the breakdown appears here.",
            style = t.bodySm,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}
