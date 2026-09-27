package com.fixmoney.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.shared.home.CategorySlice
import com.fixmoney.ui.AppTheme

/**
 * Replaces the old Home's MPAndroidChart pie + legend RecyclerView. Drawn with
 * Canvas rather than a chart library: Vico (the plan's suggestion) is another
 * dependency to clear the iosX64 + Kotlin-version gate for, and a donut is a
 * handful of arcs.
 */
@Composable
fun SpendingDonut(slices: List<CategorySlice>) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bgCard)
            .border(1.dp, colors.cardStroke, RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(140.dp)) {
            val stroke = 42.dp.toPx()
            val inset = stroke / 2
            val diameter = size.minDimension - stroke
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.percent / 100f * 360f
                drawArc(
                    color = parseHexColor(slice.colorHex),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
                startAngle += sweep
            }
        }

        Spacer(Modifier.width(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            slices.take(5).forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .background(parseHexColor(slice.colorHex))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${slice.name} ${slice.percent}%",
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

/** "#FF6B6B" or "#AARRGGBB" -> Color. Falls back to grey on anything odd. */
internal fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return Color(0xFFB0BEC5)
    return when (clean.length) {
        6 -> Color(value or 0xFF000000L)   // no alpha in the hex, so make it opaque
        8 -> Color(value)
        else -> Color(0xFFB0BEC5)
    }
}
