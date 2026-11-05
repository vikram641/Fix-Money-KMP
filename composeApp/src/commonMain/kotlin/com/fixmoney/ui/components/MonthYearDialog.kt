package com.fixmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_calendar
import com.fixmoney.resources.nd_check_circle
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * The date / month picker, rebuilt to the 2026 mockup
 * (`fixmoney_select_date_picker_obsidian_theme`).
 *
 * One composable still serves both uses, as the old app's
 * `MonthYearPickerDialog` and `SelectDateDialog` did: `showDays = false` stops
 * at the month grid, `showDays = true` adds the day grid under it.
 *
 * [initial] and the value handed to [onConfirm] are `yyyy-MM` or `yyyy-MM-dd`
 * to match - the same strings the rest of the app stores.
 *
 * The mockup's "FISCAL '26" badge is **not** here: a fiscal year is a real
 * concept that this app does not model, and a badge asserting one would be
 * decoration standing in for a feature. The year stepper carries an ACTIVE
 * marker instead, which is true - it marks the year containing today.
 */
@Composable
fun MonthYearDialog(
    title: String,
    initial: String,
    showDays: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = Design.colors
    val t = designType()

    val parts = initial.split("-")
    var year by remember { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var month by remember { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 1) }
    var day by remember { mutableIntStateOf(parts.getOrNull(2)?.toIntOrNull() ?: 1) }

    val maxDay = daysInMonth(year, month)
    if (day > maxDay) day = maxDay

    val thisMonth = remember { currentMonthApi() }
    val currentYear = remember { thisMonth.substringBefore("-").toIntOrNull() ?: year }
    val currentMonth = remember { thisMonth.substringAfter("-").toIntOrNull() ?: month }

    fun jumpTo(monthKey: String) {
        year = monthKey.substringBefore("-").toIntOrNull() ?: year
        month = monthKey.substringAfter("-").toIntOrNull() ?: month
        day = day.coerceAtMost(daysInMonth(year, month))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(Res.drawable.nd_calendar, colors.accent, 18.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = t.headlineSm, color = colors.textPrimary)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChip("This month", Modifier.weight(1f)) { jumpTo(thisMonth) }
                QuickChip("Last month", Modifier.weight(1f)) { jumpTo(shiftKey(thisMonth, -1)) }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(CircleShape)
                    .background(colors.surface).padding(5.dp)
            ) {
                StepperButton("‹") { year-- }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$year", style = t.headlineSm, color = colors.textPrimary)
                    if (year == currentYear) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(CircleShape)
                                .background(colors.positive.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", style = t.labelCaps, color = colors.positive)
                        }
                    }
                }
                StepperButton("›") { year++ }
            }

            Spacer(Modifier.height(14.dp))
            // Three across, as the mockup. Twelve cells always fit, so a plain
            // chunked Column beats a lazy grid inside a dialog that has no
            // height to give it.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { m ->
                            GridCell(
                                label = MONTHS[m - 1],
                                selected = m == month,
                                isToday = year == currentYear && m == currentMonth,
                                modifier = Modifier.weight(1f)
                            ) {
                                month = m
                                day = day.coerceAtMost(daysInMonth(year, m))
                            }
                        }
                    }
                }
            }

            if (showDays) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))
                Spacer(Modifier.height(14.dp))
                val today = remember { todayIso() }
                val todayParts = today.split("-")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..maxDay).chunked(7).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { d ->
                                GridCell(
                                    label = "$d",
                                    selected = d == day,
                                    isToday = todayParts.getOrNull(0)?.toIntOrNull() == year &&
                                        todayParts.getOrNull(1)?.toIntOrNull() == month &&
                                        todayParts.getOrNull(2)?.toIntOrNull() == d,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                ) { day = d }
                            }
                            // Keeps the last row's cells the width of a full one.
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(colors.surface)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", style = t.titleMd, color = colors.textPrimary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
                        .clickable {
                            val mm = month.toString().padStart(2, '0')
                            onConfirm(
                                if (showDays) "$year-$mm-${day.toString().padStart(2, '0')}"
                                else "$year-$mm"
                            )
                        }
                        .padding(vertical = 15.dp)
                ) {
                    LineIcon(Res.drawable.nd_check_circle, Color.White, 17.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("Confirm", style = t.titleMd, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Box(
        modifier.clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.stroke, CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = t.numericSm, color = colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    val colors = Design.colors
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 20.sp, color = Color.White)
    }
}

/**
 * One month or day cell. [isToday] rings the cell that is actually today or the
 * current month, which is the only way to tell "selected" from "now" once the
 * user has moved the selection somewhere else.
 */
@Composable
private fun GridCell(
    label: String,
    selected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Box(
        modifier
            .height(if (compact) 38.dp else 44.dp)
            .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (!selected && isToday) {
                    Modifier.border(
                        1.dp, colors.accent.copy(alpha = 0.6f),
                        RoundedCornerShape(if (compact) 12.dp else 14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (compact) t.bodyMd else t.titleMd,
            color = when {
                selected -> Color.White
                isToday -> colors.accent
                else -> colors.textMuted
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** "2026-10" shifted by whole months, wrapping the year. */
private fun shiftKey(month: String, delta: Int): String {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return month
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val total = year * 12 + (m - 1) + delta
    return "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
}

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}
