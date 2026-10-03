package com.fixmoney.ui.budget

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fixmoney.resources.*
import com.fixmoney.shared.expense.BudgetProgress
import com.fixmoney.shared.model.Category
import com.fixmoney.ui.Design
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.formatIndian
import kotlinx.coroutines.delay

/**
 * "Edit Budget Limit", from
 * `~/Downloads/stitch_fixmoney_fintech_app_design/fixmoney_set_budget_limit/`.
 *
 * A `Dialog` rather than a `ModalBottomSheet`: the sheet component is Material3
 * and its Compose Multiplatform support lags, while a full-width dialog pinned
 * to the bottom gives the same shape with no platform risk.
 *
 * **What the mockup has that this does not**, and why - each is a switch with
 * nothing behind it, which is worse than its absence:
 *
 * - **Cadence Period (Monthly / Weekly).** Budget rows are keyed by `yyyy-MM`;
 *   there is no weekly cap in the schema.
 * - **Smart Pace Guard** and **Rollover Excess.** Neither rule exists. The
 *   screen's guard card is computed on the fly, not a stored alert.
 * - **Precision Mode**, the history and reset buttons in the header: no edit
 *   history is kept.
 */
@Composable
fun EditBudgetSheet(
    existing: BudgetProgress?,
    categories: List<Category>,
    takenCategoryIds: Set<String>,
    currencySymbol: String,
    daysLeft: Int,
    onDismiss: () -> Unit,
    onSave: (Category, Int) -> Unit,
    onRemove: (String) -> Unit
) {
    val colors = Design.colors
    val t = designType()

    // Editing locks the category; adding offers the ones without a cap yet, so
    // saving can never silently overwrite an envelope the user is not looking at.
    val selectable = remember(categories, takenCategoryIds, existing) {
        if (existing != null) categories.filter { it.id == existing.categoryId }
        else categories.filter { it.id !in takenCategoryIds }
    }
    var selected by remember(selectable) {
        mutableStateOf(selectable.firstOrNull { it.id == existing?.categoryId } ?: selectable.firstOrNull())
    }
    var amount by remember {
        mutableStateOf(
            TextFieldValue(
                (existing?.limit ?: 0).takeIf { it > 0 }?.toString().orEmpty()
            ).let { it.copy(selection = TextRange(it.text.length)) }
        )
    }
    var confirmRemove by remember { mutableStateOf(false) }

    val limit = amount.text.toIntOrNull() ?: 0
    val spent = existing?.spent ?: 0
    val canSave = limit > 0 && selected != null

    fun setAmount(value: Int) {
        val clamped = value.coerceIn(0, 9_999_999)
        val text = if (clamped == 0) "" else clamped.toString()
        amount = TextFieldValue(text, TextRange(text.length))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(colors.surfaceCard)
                    .border(
                        1.dp, colors.stroke,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape).background(colors.surfaceHigh)
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceHigh)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 15.sp, color = colors.textPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (existing != null) "Edit budget limit" else "Add budget limit",
                        style = t.headlineSm,
                        color = colors.textPrimary
                    )
                }

                if (selectable.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Every category already has a cap this month. Tap one on the board to change it.",
                        style = t.bodyMd,
                        color = colors.textMuted
                    )
                    Spacer(Modifier.height(24.dp))
                    return@Column
                }

                Spacer(Modifier.height(18.dp))
                Text("ALLOCATED ENVELOPE", style = t.labelCaps, color = colors.textMuted)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectable.forEach { category ->
                        val isSelected = category.id == selected?.id
                        val tint = categoryTint(category.color, category.icon, colors)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (isSelected) tint.copy(alpha = 0.18f) else colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) tint.copy(alpha = 0.5f) else colors.stroke,
                                    CircleShape
                                )
                                .clickable { selected = category }
                                .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape)
                                    .background(tint.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LineIcon(categoryGlyph(category.icon), tint, 14.dp)
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                category.name,
                                style = t.numericSm,
                                color = if (isSelected) colors.textPrimary else colors.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.stroke, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Text("NEW MONTHLY CEILING", style = t.labelCaps, color = colors.textMuted)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            currencySymbol,
                            style = t.headlineSm,
                            color = colors.textMuted
                        )
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = amount,
                            onValueChange = { input ->
                                val digits = input.text.filter { it.isDigit() }.take(7)
                                amount = TextFieldValue(digits, TextRange(digits.length))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = t.displayHero.copy(fontSize = 30.sp, color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.weight(1f)
                        )
                        Stepper("−", colors) { setAmount(limit - STEP) }
                        Spacer(Modifier.width(8.dp))
                        Stepper("+", colors) { setAmount(limit + STEP) }
                    }

                    if (existing != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("Current cap", style = t.bodySm, color = colors.textMuted)
                                Text(
                                    "$currencySymbol${formatIndian(existing.limit)}",
                                    style = t.numericSm,
                                    color = colors.textPrimary
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Spent", style = t.bodySm, color = colors.textMuted)
                                Text(
                                    "$currencySymbol${formatIndian(spent)} (${existing.rawPercent}%)",
                                    style = t.numericSm,
                                    color = if (existing.isOver) colors.negative else colors.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(500, 1_000, 2_000).forEach { bump ->
                        QuickChip("+$currencySymbol${formatIndian(bump)}", colors, t) {
                            setAmount(limit + bump)
                        }
                    }
                    // Only when there is something to match: suggesting "match
                    // spend" on an envelope with no spend is a no-op dressed up
                    // as a shortcut.
                    if (spent > 0 && spent != limit) {
                        QuickChip("Match $currencySymbol${formatIndian(spent)}", colors, t) {
                            setAmount(spent)
                        }
                    }
                }

                if (limit > 0) {
                    Spacer(Modifier.height(16.dp))
                    RunwayCard(
                        limit = limit,
                        spent = spent,
                        daysLeft = daysLeft,
                        currencySymbol = currencySymbol
                    )
                }

                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().clip(CircleShape)
                        .background(
                            if (canSave) {
                                Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        colors.accent.copy(alpha = 0.3f),
                                        colors.accent.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        )
                        .clickable(enabled = canSave) { selected?.let { onSave(it, limit) } }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (limit > 0) "Save limit ($currencySymbol${formatIndian(limit)})" else "Save limit",
                        style = t.titleMd,
                        color = Color.White
                    )
                }

                if (existing != null) {
                    Spacer(Modifier.height(12.dp))
                    // Two taps rather than a nested dialog: a confirm dialog on
                    // top of a dialog is awkward on both platforms, and the
                    // label states plainly what the second tap will do. The arm
                    // lapses after a few seconds so a stray tap cannot leave a
                    // destructive action primed.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (confirmRemove) onRemove(existing.id) else confirmRemove = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (confirmRemove) "Tap again to remove this limit"
                            else "Remove limit for this category",
                            style = t.numericSm,
                            color = colors.negative
                        )
                    }
                    if (confirmRemove) {
                        LaunchedEffect(confirmRemove) {
                            delay(4000)
                            confirmRemove = false
                        }
                    }
                }
            }
        }
    }
}

private const val STEP = 500

@Composable
private fun Stepper(glyph: String, colors: com.fixmoney.ui.DesignColors, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 19.sp, color = colors.textPrimary)
    }
}

@Composable
private fun QuickChip(
    label: String,
    colors: com.fixmoney.ui.DesignColors,
    t: com.fixmoney.ui.DesignType,
    onClick: () -> Unit
) {
    Box(
        Modifier.clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.stroke, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = t.numericSm, color = colors.textMuted, maxLines = 1)
    }
}

/**
 * The mockup's "Projected Daily Runway": what the proposed ceiling leaves per
 * day for the rest of the cycle, and how that compares with the current cap.
 */
@Composable
private fun RunwayCard(limit: Int, spent: Int, daysLeft: Int, currencySymbol: String) {
    val colors = Design.colors
    val t = designType()
    val left = (limit - spent).coerceAtLeast(0)
    val perDay = if (daysLeft > 0) left / daysLeft else left
    val healthy = limit > spent

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LineIcon(Res.drawable.nd_trend_up, colors.accent, 15.dp)
            Spacer(Modifier.width(7.dp))
            Text("PROJECTED DAILY RUNWAY", style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            val tone = if (healthy) colors.positive else colors.negative
            Box(
                Modifier.clip(CircleShape).background(tone.copy(alpha = 0.15f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(if (healthy) "Healthy pace" else "Already over", style = t.labelCaps, color = tone)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$currencySymbol${formatIndian(perDay)}",
                style = t.displayHero.copy(fontSize = 26.sp),
                color = colors.textPrimary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (daysLeft > 0) "/day for $daysLeft days" else "left this cycle",
                style = t.bodySm,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (healthy) {
                "A $currencySymbol${formatIndian(limit)} cap leaves " +
                    "$currencySymbol${formatIndian(left)} for the rest of the cycle."
            } else {
                "Spending has already passed $currencySymbol${formatIndian(limit)} this month."
            },
            style = t.bodySm,
            color = colors.textMuted,
            overflow = TextOverflow.Ellipsis
        )
    }
}
