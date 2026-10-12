package com.fixmoney.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fixmoney.shared.home.CategorySlice
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType
import androidx.compose.material3.Text

/**
 * The redesign's donut: a thick ring with **round caps**, drawn over a solid
 * track, with the total anchored in the middle.
 *
 * Round caps are the reason each arc is inset rather than butted against the
 * next - a round cap overhangs the arc by half the stroke width at each end, so
 * drawing the raw sweeps would make neighbouring slices overlap and the last
 * one paint over the first. Each sweep is shortened by [GAP_DEGREES] and
 * started that much later, which both absorbs the overhang and gives the
 * separation the mockup has.
 *
 * A single slice at 100% is special-cased to a full circle: shortening the only
 * arc would leave a visible notch in what should be an unbroken ring.
 */
private const val GAP_DEGREES = 6f

@Composable
fun NewDonut(
    slices: List<CategorySlice>,
    centerLabel: String,
    size: Dp = 116.dp,
    stroke: Dp = 15.dp,
    centerCaption: String = "TOTAL"
) {
    val colors = Design.colors
    val t = designType()

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = colors.surfaceHigh,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )

            if (slices.isEmpty()) return@Canvas

            if (slices.size == 1) {
                drawArc(
                    color = donutColor(slices[0], 0, colors),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )
                return@Canvas
            }

            var angle = -90f
            slices.forEachIndexed { index, slice ->
                val full = slice.percent * 3.6f
                val sweep = (full - GAP_DEGREES).coerceAtLeast(1f)
                drawArc(
                    color = donutColor(slice, index, colors),
                    startAngle = angle + GAP_DEGREES / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                angle += full
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerLabel,
                style = t.titleMd,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(1.dp))
            Text(centerCaption, style = t.labelCaps, color = colors.textMuted)
        }
    }
}

/** What [parseHexColor] returns when it cannot read the string. */
private val PARSE_FALLBACK = Color(0xFFB0BEC5)

/**
 * The slice's own colour when Firestore gave it a readable one, otherwise the
 * mockup's four-colour rotation. The seeded categories do carry colours, but a
 * category added later might not, and `parseHexColor`'s grey fallback would put
 * a dead slab in a palette that is the whole point of the chart.
 */
internal fun donutColor(
    slice: CategorySlice,
    index: Int,
    colors: com.fixmoney.ui.DesignColors
): Color {
    val parsed = parseHexColor(slice.colorHex)
    if (parsed != PARSE_FALLBACK) return parsed
    val palette = listOf(
        colors.categoryFood, colors.categoryTransit,
        colors.categoryShop, colors.categoryEnt
    )
    return palette[index % palette.size]
}
