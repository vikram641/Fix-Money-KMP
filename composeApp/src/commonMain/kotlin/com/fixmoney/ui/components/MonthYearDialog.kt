package com.fixmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fixmoney.ui.AppTheme

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Port of the old app's `MonthYearPickerDialog` / `SelectDateDialog`. One
 * composable serves both: Home picks a month (`showDays = false`), Add Expense
 * picks a full date (`showDays = true`).
 *
 * [initial] and the value passed to [onConfirm] are `yyyy-MM` or `yyyy-MM-dd`
 * depending on [showDays] - the same strings the rest of the app stores.
 */
@Composable
fun MonthYearDialog(
    title: String,
    initial: String,
    showDays: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = AppTheme.colors
    val parts = initial.split("-")
    var year by remember { mutableIntStateOf(parts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var month by remember { mutableIntStateOf(parts.getOrNull(1)?.toIntOrNull() ?: 1) }
    var day by remember { mutableIntStateOf(parts.getOrNull(2)?.toIntOrNull() ?: 1) }

    val maxDay = daysInMonth(year, month)
    if (day > maxDay) day = maxDay

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.bgCard)
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(colors.inputBg).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepperButton("‹") { year-- }
                Text(
                    year.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                StepperButton("›") { year++ }
            }

            Spacer(Modifier.height(18.dp))
            // 3 x 4 month grid, as the old dialog laid it out.
            for (row in 0 until 4) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for (col in 0 until 3) {
                        val m = row * 3 + col + 1
                        val selected = m == month
                        Box(
                            modifier = Modifier.weight(1f).padding(3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) colors.accent else Color.Transparent)
                                .clickable { month = m }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                MONTHS[m - 1],
                                fontSize = 16.sp,
                                color = if (selected) Color.White else colors.textPrimary
                            )
                        }
                    }
                }
            }

            if (showDays) {
                Spacer(Modifier.height(14.dp))
                val rows = (maxDay + 6) / 7
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val d = row * 7 + col + 1
                            if (d > maxDay) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val selected = d == day
                                Box(
                                    modifier = Modifier.weight(1f).padding(2.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) colors.accent else Color.Transparent)
                                        .clickable { day = d }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.toString(),
                                        fontSize = 15.sp,
                                        color = if (selected) Color.White else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = colors.cardStroke)
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.weight(1f).clip(CircleShape)
                        .background(colors.inputBg).clickable(onClick = onDismiss)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", fontSize = 16.sp, color = colors.textSecondary)
                }
                Box(
                    modifier = Modifier.weight(1f).clip(CircleShape)
                        .background(colors.accent)
                        .clickable {
                            val mm = month.toString().padStart(2, '0')
                            onConfirm(
                                if (showDays) "$year-$mm-${day.toString().padStart(2, '0')}"
                                else "$year-$mm"
                            )
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Confirm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier.size(46.dp).clip(CircleShape).background(colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 22.sp, color = Color.White)
    }
}

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}
