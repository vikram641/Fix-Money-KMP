package com.fixmoney.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Every screen was laid out against a large phone (the 430dp-wide simulator),
 * so on a narrower device the same dp values eat more of the screen and read
 * as oversized.
 *
 * Rather than sprinkling size variants through every composable, this scales
 * **density** once at the root: `dp` and `sp` both derive from it, so the whole
 * UI shrinks or grows together and keeps its proportions. Clamped so it never
 * scales past the design size on a tablet, and never gets so small it hurts
 * readability on a compact phone.
 */
private const val DESIGN_WIDTH_DP = 430f
private const val MIN_SCALE = 0.82f
private const val MAX_SCALE = 1.0f

@Composable
fun ResponsiveUi(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val scale = (maxWidth.value / DESIGN_WIDTH_DP).coerceIn(MIN_SCALE, MAX_SCALE)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density * scale,
                // Left alone so the user's own font-size setting still applies.
                fontScale = density.fontScale
            )
        ) {
            content()
        }
    }
}
