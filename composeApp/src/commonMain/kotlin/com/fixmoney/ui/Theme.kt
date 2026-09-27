package com.fixmoney.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour token from the old app's `res/values/colors.xml`, with the
 * `values-night/colors.xml` overrides applied in [DarkColors].
 *
 * GENERATED from those two files - the old project is reference material and
 * is never read at build time, so change colours here.
 *
 * Material3's own scheme covers only a handful of roles while this app has
 * 59 semantic tokens (category colours, badge states, AI-insight card
 * tints, nav states...), so they live in [AppColors], reached through
 * [LocalAppColors] / [AppTheme]. The Material scheme is mapped onto the
 * closest tokens so stock components still look right.
 *
 * 20 tokens have a real dark variant: ai_insight_bg_end, ai_insight_bg_start, ai_insight_stroke, bg_card, bg_primary, bg_splash, card_stroke, expense_red, input_bg, progress_bg, progress_end, progress_start, text_accent, text_error, text_hint, text_primary, text_purple_light, text_secondary, text_splash_subtitle, text_warning.
 * The rest are identical in both themes, exactly as in the old app.
 */
@Immutable
data class AppColors(
    val black: Color,
    val white: Color,
    val bgScreen: Color,
    val cardBg: Color,
    val accent: Color,
    val tagBg: Color,
    val iconBg: Color,
    val red: Color,
    val deleteBg: Color,
    val gray: Color,
    val primary: Color,
    val bgSurface: Color,
    val border: Color,
    val bottomNavBg: Color,
    val expenseRed: Color,
    val bgPrimary: Color,
    val bgCard: Color,
    val cardStroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textHint: Color,
    val textAccent: Color,
    val inputBg: Color,
    val textError: Color,
    val textWarning: Color,
    val progressBg: Color,
    val progressStart: Color,
    val progressEnd: Color,
    val bgDark: Color,
    val bgSplash: Color,
    val textSplashSubtitle: Color,
    val textWhite: Color,
    val textSub: Color,
    val purple: Color,
    val green: Color,
    val bgCardInner: Color,
    val textGreen: Color,
    val textPurpleLight: Color,
    val accentPurple: Color,
    val accentPurpleFab: Color,
    val textLight: Color,
    val progressBarFill: Color,
    val progressBarBg: Color,
    val badgeWarningBg: Color,
    val badgeWarningText: Color,
    val badgeWarningBorder: Color,
    val categoryFood: Color,
    val categoryTransport: Color,
    val categoryBills: Color,
    val categoryOther: Color,
    val navActive: Color,
    val navInactive: Color,
    val navBg: Color,
    val avatarBg: Color,
    val avatarText: Color,
    val strokeSubtle: Color,
    val aiInsightBgStart: Color,
    val aiInsightBgEnd: Color,
    val aiInsightStroke: Color,
)

val LightColors = AppColors(
    black = Color(0xFF000000),
    white = Color(0xFFFFFFFF),
    bgScreen = Color(0xFF0B132B),
    cardBg = Color(0xFF19224E),
    accent = Color(0xFF7B61FF),
    tagBg = Color(0xFF2A2555),
    iconBg = Color(0xFF2E2A5E),
    red = Color(0xFFFF4D4D),
    deleteBg = Color(0xFFF5D6D6),
    gray = Color(0xFF888888),
    primary = Color(0xFF6200EE),
    bgSurface = Color(0xFFFFFFFF),
    border = Color(0xFFE5E9F5),
    bottomNavBg = Color(0xFFFFFFFF),
    expenseRed = Color(0xFFE53935),
    bgPrimary = Color(0xFFF7F9FF),
    bgCard = Color(0xFFFFFFFF),
    cardStroke = Color(0xFFE5E9F5),
    textPrimary = Color(0xFF1A1F36),
    textSecondary = Color(0xFF6B7280),
    textHint = Color(0xFF9CA3AF),
    textAccent = Color(0xFF6C7BFF),
    inputBg = Color(0xFFF0F4FF),
    textError = Color(0xFFDC2626),
    textWarning = Color(0xFFE53935),
    progressBg = Color(0xFFE5E9F5),
    progressStart = Color(0xFF6A5AE0),
    progressEnd = Color(0xFF9C7BFF),
    bgDark = Color(0xFF0B1A2B),
    bgSplash = Color(0xFFFFFFFF),
    textSplashSubtitle = Color(0xFF6B7280),
    textWhite = Color(0xFFFFFFFF),
    textSub = Color(0xFFA0A4B8),
    purple = Color(0xFF7B6EF6),
    green = Color(0xFF00E676),
    bgCardInner = Color(0xFF2E2E4A),
    textGreen = Color(0xFF4CAF82),
    textPurpleLight = Color(0xFFCBC3FF),
    accentPurple = Color(0xFF7C6FDD),
    accentPurpleFab = Color(0xFF6B5FD0),
    textLight = Color(0xFFAAB4C3),
    progressBarFill = Color(0xFF7C6FDD),
    progressBarBg = Color(0xFF3D3D5C),
    badgeWarningBg = Color(0x33F59E0B),
    badgeWarningText = Color(0xFFF59E0B),
    badgeWarningBorder = Color(0x80F59E0B),
    categoryFood = Color(0xFF7C6FDD),
    categoryTransport = Color(0xFF4CAF82),
    categoryBills = Color(0xFFF5A623),
    categoryOther = Color(0xFFE74C3C),
    navActive = Color(0xFFFFFFFF),
    navInactive = Color(0xFF7070A0),
    navBg = Color(0xFF1A1A2E),
    avatarBg = Color(0xFF7C6FDD),
    avatarText = Color(0xFFFFFFFF),
    strokeSubtle = Color(0x33FFFFFF),
    aiInsightBgStart = Color(0xFFEFEDFC),
    aiInsightBgEnd = Color(0xFFFBFAFF),
    aiInsightStroke = Color(0xFFE2DBFB),
)

val DarkColors = AppColors(
    black = Color(0xFF000000),
    white = Color(0xFFFFFFFF),
    bgScreen = Color(0xFF0B132B),
    cardBg = Color(0xFF19224E),
    accent = Color(0xFF7B61FF),
    tagBg = Color(0xFF2A2555),
    iconBg = Color(0xFF2E2A5E),
    red = Color(0xFFFF4D4D),
    deleteBg = Color(0xFFF5D6D6),
    gray = Color(0xFF888888),
    primary = Color(0xFF6200EE),
    bgSurface = Color(0xFFFFFFFF),
    border = Color(0xFFE5E9F5),
    bottomNavBg = Color(0xFFFFFFFF),
    expenseRed = Color(0xFFFF5C5C),
    bgPrimary = Color(0xFF0B1A2B),
    bgCard = Color(0xFF12263A),
    cardStroke = Color(0xFF2A3C52),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8FA3BF),
    textHint = Color(0xFF8FA3BF),
    textAccent = Color(0xFF6C7BFF),
    inputBg = Color(0xFF1E2B44),
    textError = Color(0xFFFF6B6B),
    textWarning = Color(0xFFBF360C),
    progressBg = Color(0xFF2A2F3A),
    progressStart = Color(0xFF6A5AE0),
    progressEnd = Color(0xFF9C7BFF),
    bgDark = Color(0xFF0B1A2B),
    bgSplash = Color(0xFF070E31),
    textSplashSubtitle = Color(0xFFA0A4B8),
    textWhite = Color(0xFFFFFFFF),
    textSub = Color(0xFFA0A4B8),
    purple = Color(0xFF7B6EF6),
    green = Color(0xFF00E676),
    bgCardInner = Color(0xFF2E2E4A),
    textGreen = Color(0xFF4CAF82),
    textPurpleLight = Color(0xFF444447),
    accentPurple = Color(0xFF7C6FDD),
    accentPurpleFab = Color(0xFF6B5FD0),
    textLight = Color(0xFFAAB4C3),
    progressBarFill = Color(0xFF7C6FDD),
    progressBarBg = Color(0xFF3D3D5C),
    badgeWarningBg = Color(0x33F59E0B),
    badgeWarningText = Color(0xFFF59E0B),
    badgeWarningBorder = Color(0x80F59E0B),
    categoryFood = Color(0xFF7C6FDD),
    categoryTransport = Color(0xFF4CAF82),
    categoryBills = Color(0xFFF5A623),
    categoryOther = Color(0xFFE74C3C),
    navActive = Color(0xFFFFFFFF),
    navInactive = Color(0xFF7070A0),
    navBg = Color(0xFF1A1A2E),
    avatarBg = Color(0xFF7C6FDD),
    avatarText = Color(0xFFFFFFFF),
    strokeSubtle = Color(0x33FFFFFF),
    aiInsightBgStart = Color(0xFF241F45),
    aiInsightBgEnd = Color(0xFF1A1830),
    aiInsightStroke = Color(0xFF3D3566),
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Shorthand: `AppTheme.colors.textSecondary` inside any composable. */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

@Composable
fun FixMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.white,
            secondary = colors.progressEnd,
            background = colors.bgPrimary,
            onBackground = colors.textPrimary,
            surface = colors.bgCard,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.cardStroke,
            error = colors.textError
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.white,
            secondary = colors.progressEnd,
            background = colors.bgPrimary,
            onBackground = colors.textPrimary,
            surface = colors.bgCard,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.cardStroke,
            error = colors.textError
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
