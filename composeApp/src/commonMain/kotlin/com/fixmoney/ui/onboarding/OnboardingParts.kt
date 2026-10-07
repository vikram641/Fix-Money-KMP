package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import com.fixmoney.resources.nd_chevron_right
import com.fixmoney.resources.Res
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import org.jetbrains.compose.resources.DrawableResource

/**
 * Shared furniture for the onboarding mockups
 * (`stitch_fixmoney_fintech_app_design (6)`-`(8)`).
 */

/**
 * The glowing rounded square the mockups put at the top of every onboarding
 * screen.
 *
 * The glow is **drawn, not blurred**: concentric rounded rectangles of falling
 * alpha. `Modifier.blur` is the obvious tool and is the one thing here that
 * does not render the same on both platforms, and an onboarding screen is the
 * first thing a user sees on either - so a few extra draw calls buy a halo that
 * is identical on Android and iOS.
 */
@Composable
fun GlowTile(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    corner: Dp = 42.dp,
    iconSize: Dp = 62.dp
) {
    val colors = Design.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Glow(diameter = size + 96.dp)
        Box(
            Modifier.size(size)
                .clip(RoundedCornerShape(corner))
                .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
                .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(icon, Color.White, iconSize)
        }
    }
}

/** The outlined capsule the mockups use to label a feature. */
@Composable
fun TagPill(
    text: String,
    icon: DrawableResource? = null,
    dot: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.stroke, CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        when {
            dot != null -> {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
            }
            icon != null -> {
                LineIcon(icon, colors.accent, 14.dp)
                Spacer(Modifier.width(7.dp))
            }
        }
        Text(text, style = t.bodySm.copy(fontSize = 13.sp), color = colors.textPrimary)
    }
}

/** Pager dots: the current step is an elongated pill, the rest are circles. */
@Composable
fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = Design.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val active = i == current
            Box(
                Modifier.padding(horizontal = 4.dp)
                    .size(width = if (active) 28.dp else 9.dp, height = 9.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.accent else colors.stroke)
            )
        }
    }
}

/**
 * The mockups' primary button: a purple gradient capsule sitting on its own
 * glow.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: DrawableResource? = null,
    height: Dp = 58.dp,
    enabled: Boolean = true
) {
    val colors = Design.colors
    val t = designType()
    // Disabled keeps the gradient but drains it, so the button still reads as
    // the primary action waiting on something rather than as a dead slab.
    val fill = if (enabled) {
        Brush.horizontalGradient(listOf(colors.fabStart, colors.fabEnd))
    } else {
        Brush.horizontalGradient(
            listOf(colors.fabStart.copy(alpha = 0.35f), colors.fabEnd.copy(alpha = 0.35f))
        )
    }
    // The bloom is part of the button, so the button has to **measure** it.
    // Drawn outside its own bounds it overlapped whatever sat above - on the
    // name step the helper line disappeared under it the moment the keyboard
    // squeezed the column.
    Box(
        modifier.height(height + GLOW_BLEED * 2),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) Glow(diameter = height + GLOW_BLEED * 2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height(height)
                .clip(CircleShape)
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick)
        ) {
            val content = if (enabled) Color.White else Color.White.copy(alpha = 0.55f)
            Text(text, style = t.titleMd, color = content)
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                LineIcon(trailing, content, 18.dp)
            }
        }
    }
}

/** How far [GlowButton]'s bloom reaches past the capsule, and reserves for. */
private val GLOW_BLEED = 16.dp

/**
 * A soft circular bloom.
 *
 * A radial gradient, because the first attempt - concentric rounded rectangles
 * of falling alpha - banded visibly on device: each ring drew its own hard
 * edge, so the halo read as nested outlines rather than light. `Modifier.blur`
 * would also do it and is the one thing here that does not render identically
 * on both platforms, which is not a trade worth making on the first screen a
 * user ever sees.
 */
@Composable
private fun Glow(diameter: Dp, modifier: Modifier = Modifier) {
    val accent = Design.colors.accent
    Box(
        modifier.size(diameter).background(
            Brush.radialGradient(
                0.0f to accent.copy(alpha = 0.30f),
                0.45f to accent.copy(alpha = 0.12f),
                1.0f to Color.Transparent
            )
        )
    )
}

/**
 * The setup steps' header: a circular back button, a progress bar and the step
 * count.
 *
 * The count is two-tone - the current step reads as the live number, the total
 * stays quiet - so the pair is legible at a glance without a label.
 */
@Composable
fun SetupHeader(step: Int, total: Int, onBack: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(colors.surfaceHigh)
                .border(1.dp, colors.stroke, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            // There is no left chevron in the icon set; the right one
            // mirrored is the same glyph and keeps the pair consistent.
            Box(Modifier.scale(scaleX = -1f, scaleY = 1f)) {
                LineIcon(Res.drawable.nd_chevron_right, colors.textPrimary, 20.dp)
            }
        }
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier.weight(1f).height(5.dp).clip(CircleShape).background(colors.stroke)
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fraction = (step.toFloat() / total).coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(colors.fabStart, colors.fabEnd)))
            )
        }
        Spacer(Modifier.width(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$step", style = t.titleMd, color = colors.textPrimary)
            Text(" / ", style = t.titleMd, color = colors.textMuted)
            Text("$total", style = t.titleMd, color = colors.textMuted)
        }
    }
}

/**
 * The outlined icon plate heading each setup step.
 *
 * Deliberately **not** [GlowTile]: that one is a solid slab of purple with a
 * white glyph, which is right when the icon is the subject of the screen. Here
 * it is a label above a question, so it is a tinted plate with an accent glyph
 * and a faint bloom - present, but not competing with the heading under it.
 */
@Composable
fun SetupIconTile(icon: DrawableResource, modifier: Modifier = Modifier) {
    val colors = Design.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(128.dp).background(
                Brush.radialGradient(
                    0.0f to colors.accent.copy(alpha = 0.20f),
                    0.5f to colors.accent.copy(alpha = 0.07f),
                    1.0f to Color.Transparent
                )
            )
        )
        Box(
            Modifier.size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.accent.copy(alpha = 0.16f))
                .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(icon, colors.accent, 32.dp)
        }
    }
}

/**
 * Indian digit grouping: last three digits, then pairs - 100000 renders as
 * `1,00,000`, not `100,000`.
 *
 * Lives here rather than on the budget step because Edit Profile and the SIP
 * calculator import it too; it moved when that screen was rebuilt.
 */
fun formatIndian(value: Int): String {
    val digits = (if (value < 0) -value else value).toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$head,$last3"
    }
    return if (value < 0) "-$grouped" else grouped
}


