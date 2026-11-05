package com.fixmoney.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The 2026 redesign's palette and type scale, taken from
 * `~/Downloads/stitch_fixmoney_fintech_app_design/`.
 *
 * **Why a second colour holder instead of growing [AppColors]**: that one is
 * *generated* from the old app's `colors.xml` and is still what every
 * not-yet-redesigned screen reads. Mixing the new surfaces into it would mean
 * either rewriting all nine screens at once or leaving tokens in it that mean
 * different things on different screens. This holder is additive - a screen
 * opts in by reading `Design.colors`, and the untouched screens keep working
 * exactly as before. Delete `AppColors` once every screen has moved over.
 *
 * The design is dark-first (the mockups are all dark). The light values are
 * sensible counterparts, not design-sourced - check them against a light
 * mockup before trusting them.
 */
@Immutable
data class DesignColors(
    /** Page background. */
    val surface: Color,
    /** Card background, one step up from the page. */
    val surfaceCard: Color,
    /** Inner chips and the donut's empty track. */
    val surfaceHigh: Color,
    /** The bottom navigation bar - deliberately *darker* than the page. */
    val surfaceNav: Color,
    /** The hairline every card is outlined with. */
    val stroke: Color,
    /** The slightly warmer hairline on the AI card. */
    val strokeAccent: Color,
    val textPrimary: Color,
    /** Secondary copy, labels, inactive nav items. */
    val textMuted: Color,
    /** The lavender used for links, active nav and the AI chip. */
    val accent: Color,
    /** The saturated violet behind filled buttons and the FAB. */
    val accentStrong: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val fabStart: Color,
    val fabEnd: Color,
    /** "On track", the progress bar's tip, the live dot. */
    val positive: Color,
    /** Over-budget figures and the "up 32%" style emphasis. */
    val negative: Color,
    val categoryFood: Color,
    val categoryTransit: Color,
    val categoryShop: Color,
    val categoryEnt: Color,
    val isDark: Boolean
)

val DesignDark = DesignColors(
    surface = Color(0xFF0E141D),
    surfaceCard = Color(0xFF161C26),
    surfaceHigh = Color(0xFF242A35),
    surfaceNav = Color(0xFF080E18),
    stroke = Color(0x14FFFFFF),
    strokeAccent = Color(0x38917EFF),
    textPrimary = Color(0xFFDDE2F1),
    textMuted = Color(0xFF928EA1),
    accent = Color(0xFFC9BFFF),
    accentStrong = Color(0xFF917EFF),
    heroStart = Color(0xFF7B61FF),
    heroEnd = Color(0xFF4D35D4),
    fabStart = Color(0xFF9B84FF),
    fabEnd = Color(0xFF6B4FFF),
    positive = Color(0xFF41E185),
    negative = Color(0xFFFF6B6B),
    categoryFood = Color(0xFFFF6B6B),
    categoryTransit = Color(0xFF4ECDC4),
    categoryShop = Color(0xFF45B7D1),
    categoryEnt = Color(0xFFC56BFF),
    isDark = true
)

val DesignLight = DesignColors(
    surface = Color(0xFFF6F7FB),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEFF1F7),
    surfaceNav = Color(0xFFFFFFFF),
    stroke = Color(0x14000000),
    strokeAccent = Color(0x38917EFF),
    textPrimary = Color(0xFF151A23),
    textMuted = Color(0xFF6E7180),
    accent = Color(0xFF6B4FFF),
    accentStrong = Color(0xFF7B61FF),
    heroStart = Color(0xFF7B61FF),
    heroEnd = Color(0xFF4D35D4),
    fabStart = Color(0xFF9B84FF),
    fabEnd = Color(0xFF6B4FFF),
    positive = Color(0xFF12A557),
    negative = Color(0xFFE2574C),
    categoryFood = Color(0xFFFF6B6B),
    categoryTransit = Color(0xFF4ECDC4),
    categoryShop = Color(0xFF45B7D1),
    categoryEnt = Color(0xFFC56BFF),
    isDark = false
)

val LocalDesignColors = staticCompositionLocalOf { DesignDark }

/** `Design.colors.surfaceCard` inside any redesigned composable. */
object Design {
    val colors: DesignColors
        @Composable get() = LocalDesignColors.current
}
