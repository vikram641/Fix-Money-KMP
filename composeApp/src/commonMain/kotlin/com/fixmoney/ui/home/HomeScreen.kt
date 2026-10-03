package com.fixmoney.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import kotlin.math.abs
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.resources.*
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.home.HomeStateFactory
import com.fixmoney.shared.home.HomeUiState
import com.fixmoney.shared.home.RecentExpense
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.plusDaysIso
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.ui.Design
import com.fixmoney.ui.DesignColors
import com.fixmoney.ui.budget.prettyMonthLabel
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.shortCategoryLabel
import com.fixmoney.ui.components.CollapsingScreen
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.designType
import com.fixmoney.ui.isEnabled
import com.fixmoney.ui.onboarding.formatIndian
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Home, rebuilt to the 2026 mockup
 * (`~/Downloads/stitch_fixmoney_fintech_app_design/fixmoney_home/`).
 *
 * Layout, top to bottom: a translucent app bar, the greeting row with the AI
 * and avatar buttons, the purple hero card, a two-up "bento" of the breakdown
 * donut and the AI insight, then Recent.
 *
 * **Deliberate differences from the mockup**, all because the data behind them
 * does not exist rather than for taste:
 *
 * - Recent rows read "Food · Today" and not "Food · Today, 4:15 PM". The
 *   `expenses` table stores a `yyyy-MM-dd` date and no clock time (it is the
 *   old app's schema, kept so an update can read an existing install), so a
 *   time would have to be invented.
 * - The AI card's copy comes from `HomeInsightEngine`, which is arithmetic over
 *   local rows. The mockup's "Swiggy orders on weekends are driving the spike"
 *   implies merchant-level analysis the app has no data for.
 * - The bell is inert: there is no notification feed to open.
 */
/** Scroll delta, in pixels, before the FAB changes state. */
private const val FAB_SCROLL_THRESHOLD = 14

@Composable
fun HomeScreen(
    onAddExpense: () -> Unit = {},
    onAskAi: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onSeeAllExpenses: () -> Unit = {},
    onOpenExpense: (String) -> Unit = {}
) {
    val colors = Design.colors
    val t = designType()
    val factory: HomeStateFactory = koinInject()
    var month by remember { mutableStateOf(currentMonthApi()) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val flow = remember(month) { factory.observe(month) }
    val state by flow.collectAsStateWithLifecycle(remember(month) { factory.build(month = month) })

    // The FAB gets out of the way while reading down the page and comes back on
    // the first upward flick. `scroll.value` is the whole signal - a plain
    // Column has no `firstVisibleItemIndex` to lean on - so the direction is the
    // sign of the delta since the last accepted sample.
    val scroll = rememberScrollState()
    var fabVisible by remember { mutableStateOf(true) }
    // Keyed on the state object, not `scroll.value`: keying on the value makes
    // Compose cancel and restart the effect on **every scrolled pixel**. One
    // long-lived collector over `snapshotFlow` sees the same changes, and the
    // anchor can be a plain local instead of more state.
    LaunchedEffect(scroll) {
        var anchor = scroll.value
        snapshotFlow { scroll.value }.collect { current ->
            if (current <= 0) {
                // Always visible at rest at the top, whatever the last delta was.
                fabVisible = true
                anchor = 0
                return@collect
            }
            val delta = current - anchor
            // A dead zone, or a few pixels of fling wobble flaps the button.
            if (abs(delta) >= FAB_SCROLL_THRESHOLD) {
                fabVisible = delta < 0
                anchor = current
            }
        }
    }

    CollapsingScreen(
        title = "Home",
        scroll = scroll,
        onOpenProfile = onOpenProfile,
        sticky = {
            Column(Modifier.padding(horizontal = 20.dp)) {
                GreetingRow(
                    state = state,
                    monthLabel = prettyMonthLabel(month),
                    onPickMonth = { showMonthPicker = true },
                    onAskAi = onAskAi
                )
                Spacer(Modifier.height(16.dp))
            }
        },
        body = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(state)
                BentoRow(state = state, onAskAi = onAskAi, onSeeAll = onSeeAllExpenses)
                RecentSection(
                    state = state,
                    onSeeAll = onSeeAllExpenses,
                    onOpenExpense = onOpenExpense,
                    onAddExpense = onAddExpense
                )
                // Clears the FAB and the bottom bar.
                Spacer(Modifier.height(92.dp))
            }
        }
    )

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = fabVisible,
            enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.8f),
            exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Fab(onClick = onAddExpense)
        }
    }

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

/* ---------------------------------------------------------------- app bar */

/* -------------------------------------------------------------- greeting */

@Composable
private fun GreetingRow(
    state: HomeUiState,
    monthLabel: String,
    onPickMonth: () -> Unit,
    onAskAi: () -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                // `greeting` carries a newline from the old two-line layout;
                // this design puts it on one.
                state.greeting.replace("\n", ", ").removeSuffix(" 👋"),
                style = t.headlineLg,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPickMonth)
                    .padding(vertical = 2.dp, horizontal = 2.dp)
            ) {
                Text(monthLabel, style = t.numericSm, color = colors.textMuted)
                Spacer(Modifier.width(4.dp))
                LineIcon(Res.drawable.nd_chevron_right, colors.textMuted, 12.dp)
            }
        }
        if (isEnabled(FeatureFlag.FixooChat)) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f))
                    .clickable(onClick = onAskAi),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_sparkle_duo, colors.accent, 20.dp)
                // The mockup's "live" dot, top-right of the button.
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .size(7.dp).clip(CircleShape).background(colors.accent)
                )
            }
        }
    }
}

/* ------------------------------------------------------------- hero card */

@Composable
private fun HeroCard(state: HomeUiState) {
    val colors = Design.colors
    val t = designType()
    val onHero = Color(0xFFE4DEFF)

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("SPENT THIS MONTH", style = t.labelCaps, color = Color(0xFFE0D9FF))
            Spacer(Modifier.weight(1f))
            if (state.daysLeft > 0) {
                LineIcon(Res.drawable.nd_calendar, onHero.copy(alpha = 0.8f), 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "${state.daysLeft} Days Left",
                    style = t.labelCaps.copy(fontWeight = FontWeight.Medium),
                    color = onHero.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.currencySymbol}${formatIndian(state.totalSpent)}",
                style = t.displayHero,
                color = Color.White
            )
            Spacer(Modifier.width(3.dp))
            Text(
                ".00",
                style = t.bodySm,
                color = onHero.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.20f))
        ) {
            if (state.percentUsed > 0) {
                Box(
                    Modifier.fillMaxWidth(state.percentUsed / 100f).fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White,
                                    if (state.isOnTrack) colors.positive else colors.negative
                                )
                            )
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.totalBudget > 0) {
                    "${state.percentUsed}% of ${state.currencySymbol}${formatIndian(state.totalBudget)} budget"
                } else {
                    "No budget set for this month"
                },
                style = t.numericSm.copy(fontWeight = FontWeight.Medium),
                color = onHero
            )
            Spacer(Modifier.weight(1f))
            Text(
                state.paceLabel,
                style = t.numericSm.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFE0D9FF)
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroStat("THIS WEEK", "${state.currencySymbol}${formatIndian(state.weekSpent)}", Modifier.weight(1f))
            HeroDivider()
            HeroStat("DAILY AVG", "${state.currencySymbol}${formatIndian(state.dailyAverage)}", Modifier.weight(1f))
            HeroDivider()
            HeroStat("LEFT", "${state.currencySymbol}${formatIndian(state.remaining)}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val t = designType()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = t.labelCaps, color = Color(0xFFD0C6FF))
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = t.numericMd.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.15f)))
}

/* ------------------------------------------------------------ bento grid */

@Composable
private fun BentoRow(state: HomeUiState, onAskAi: () -> Unit, onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BreakdownCard(state, Modifier.weight(1f), onSeeAll)
        if (isEnabled(FeatureFlag.AiInsights)) {
            InsightCardNew(state, Modifier.weight(1f), onAskAi)
        }
    }
}

@Composable
private fun BreakdownCard(state: HomeUiState, modifier: Modifier, onSeeAll: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val slices = state.breakdown.take(4)

    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("BREAKDOWN", style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(CircleShape).clickable(onClick = onSeeAll).padding(2.dp)) {
                LineIcon(Res.drawable.nd_chevron_right, colors.textMuted, 14.dp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NewDonut(slices = slices, centerLabel = compactAmount(state.currencySymbol, state.totalSpent))
        }
        Spacer(Modifier.height(10.dp))

        if (slices.isEmpty()) {
            Text("No spending yet", style = t.bodySm, color = colors.textMuted)
        } else {
            // A 2x2 of dot + name + percent, as in the mockup.
            slices.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    pair.forEachIndexed { indexInPair, slice ->
                        val index = slices.indexOf(slice)
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(7.dp).clip(CircleShape)
                                    .background(donutColor(slice, index, colors))
                            )
                            Spacer(Modifier.width(4.dp))
                            // Name and percent are **one** Text, not two in a
                            // Row. As two, the unweighted percent was measured
                            // against the full row width first and the weighted
                            // name got whatever was left - which in half a card
                            // on a compact phone was about two characters
                            // ("Sh…"). One annotated string is measured once and
                            // ellipsises as a unit.
                            Text(
                                buildAnnotatedString {
                                    append(shortCategoryLabel(slice.name))
                                    append(' ')
                                    withStyle(SpanStyle(color = colors.textMuted)) {
                                        append("${slice.percent}%")
                                    }
                                },
                                // A point smaller than bodySm, and tracking
                                // removed: half a card on a compact phone is
                                // ~74dp per entry and "Transit 10%" does not fit
                                // at 12sp however the spacing is arranged.
                                style = t.bodySm.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (indexInPair == 0 && pair.size == 2) Spacer(Modifier.width(6.dp))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InsightCardNew(state: HomeUiState, modifier: Modifier, onAskAi: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val insight = state.insight

    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.strokeAccent, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_sparkle, colors.accent, 12.dp)
            }
            Spacer(Modifier.width(6.dp))
            Text("AI INSIGHT", style = t.labelCaps, color = colors.accent)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).clip(CircleShape).background(colors.positive))
        }

        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f)) {
            if (insight == null) {
                Text(
                    "Nothing unusual in your spending this month — keep going.",
                    style = t.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
            } else {
                Text(
                    insightHeadline(insight.categoryName, insight.categoryPercentChange),
                    style = t.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
                insight.projectedOverageDay?.let { day ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "On track to exceed budget around day $day.",
                        style = t.bodySm,
                        color = colors.textMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAskAi)
        ) {
            Text("Ask Fixoo", style = t.numericSm, color = colors.accent)
            Spacer(Modifier.width(5.dp))
            LineIcon(Res.drawable.nd_arrow_right, colors.accent, 15.dp)
        }
    }
}

private fun insightHeadline(category: String?, percent: Int?): String = when {
    category != null && percent != null -> "$category is up $percent% vs your 3-week average."
    else -> "You're on track to exceed your budget this month."
}

/* ----------------------------------------------------------------- recent */

@Composable
private fun RecentSection(
    state: HomeUiState,
    onSeeAll: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onAddExpense: () -> Unit
) {
    val colors = Design.colors
    val t = designType()

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Recent", style = t.headlineSm, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "See all",
                style = t.numericSm.copy(fontWeight = FontWeight.Medium),
                color = colors.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp)
        ) {
            if (state.recent.isEmpty()) {
                EmptyRecent(onAddExpense)
            } else {
                state.recent.forEachIndexed { index, row ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))
                    }
                    RecentRow(row, state.currencySymbol) { onOpenExpense(row.id) }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(row: RecentExpense, symbol: String, onClick: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val tint = categoryTint(row.categoryColor, row.categoryIcon, colors)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(categoryGlyph(row.categoryIcon), tint, 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = t.titleMd,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${row.categoryName} • ${relativeDay(row.dateIso)}",
                style = t.bodySm,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "-$symbol${formatIndian(row.amount)}",
            style = t.numericMd.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )
    }
}

@Composable
private fun EmptyRecent(onAddExpense: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No expenses yet", style = t.titleMd, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add your first one and this list fills in.",
            style = t.bodySm,
            color = colors.textMuted
        )
        Spacer(Modifier.height(14.dp))
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

/* -------------------------------------------------------------------- fab */

@Composable
private fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Design.colors
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 20.dp, bottom = 20.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(Res.drawable.nd_plus, Color.White, 26.dp)
    }
}

/* ----------------------------------------------------------------- shared */

/** 12480 -> "₹12.4k", 940 -> "₹940" - the donut centre has no room for more. */
internal fun compactAmount(symbol: String, value: Int): String = when {
    value >= 10_000_000 -> "$symbol${value / 10_000_000}.${(value % 10_000_000) / 1_000_000}cr"
    value >= 100_000 -> "$symbol${value / 100_000}.${(value % 100_000) / 10_000}L"
    value >= 1_000 -> "$symbol${value / 1_000}.${(value % 1_000) / 100}k"
    else -> "$symbol$value"
}

/** "Today" / "Yesterday" / "12 Sep" - the app stores no clock time. */
internal fun relativeDay(iso: String): String {
    val today = todayIso()
    if (iso == today) return "Today"
    if (iso == plusDaysIso(today, -1)) return "Yesterday"
    val parts = iso.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return iso
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return iso
    val names = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "$day ${names.getOrElse(month - 1) { "" }}"
}

/** The mockup's four accent colours, keyed off the category's icon name. */
private fun recentTint(row: RecentExpense, colors: DesignColors): Color {
    val parsed = parseHexColor(row.categoryColor)
    if (parsed != Color(0xFFB0BEC5)) return parsed
    return when (row.categoryIcon.lowercase()) {
        "ic_food", "food" -> colors.categoryFood
        "ic_transport", "transport" -> colors.categoryTransit
        "ic_shopping", "shopping" -> colors.categoryShop
        else -> colors.categoryEnt
    }
}

/**
 * The line glyph for a category. The old PNG icons are full-colour and would
 * fight the mockup's tinted-circle treatment, so the redesign maps the same
 * `icon` names onto the new stroke set and falls back to a neutral mark.
 */
private fun recentGlyph(icon: String): DrawableResource = when (icon.lowercase()) {
    "ic_food", "food" -> Res.drawable.nd_cat_food
    "ic_transport", "transport" -> Res.drawable.nd_cat_transport
    "ic_shopping", "shopping" -> Res.drawable.nd_cat_shopping
    "ic_bills", "bills" -> Res.drawable.nd_cat_bills
    "ic_health", "health" -> Res.drawable.nd_cat_health
    "ic_entertainment", "entertainment" -> Res.drawable.nd_cat_entertainment
    else -> Res.drawable.nd_cat_other
}
