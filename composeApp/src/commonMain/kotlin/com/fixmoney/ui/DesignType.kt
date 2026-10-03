package com.fixmoney.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.plus_jakarta_bold
import com.fixmoney.resources.plus_jakarta_medium
import com.fixmoney.resources.plus_jakarta_regular
import com.fixmoney.resources.plus_jakarta_semibold
import org.jetbrains.compose.resources.Font

/**
 * Plus Jakarta Sans and the redesign's type scale, lifted verbatim from the
 * mockup's Tailwind config (sizes, line heights, letter spacing and weights).
 *
 * The four weights are **static instances cut from the official variable
 * font** with `fontTools.varLib.instancer` - Google Fonts publishes only the
 * variable TTF for this family, and Compose's `Font()` takes a weight rather
 * than an axis value. Regenerate the same way if a weight is ever added.
 *
 * Poppins stays in `Type.kt` and stays the Material theme default, so the
 * screens that have not been redesigned keep the typeface they were built
 * against. A redesigned screen opts in by using these styles explicitly.
 */
@Composable
fun jakarta() = FontFamily(
    Font(Res.font.plus_jakarta_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_bold, FontWeight.Bold)
)

/** The mockup's named styles. One object so a screen reads `t.heroFigure`. */
class DesignType(private val family: FontFamily) {
    private fun s(
        size: Int, line: Int, weight: FontWeight, tracking: Float = 0f
    ) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em
    )

    /** 34/40 -0.025em 700 - the hero amount. */
    val displayHero = s(34, 40, FontWeight.Bold, -0.025f)
    /** 28/34 -0.02em 700 - "Good evening, Vik". */
    val headlineLg = s(28, 34, FontWeight.Bold, -0.02f)
    /** 18/24 -0.01em 600 - section headings and the app-bar title. */
    val headlineSm = s(18, 24, FontWeight.SemiBold, -0.01f)
    /** 16/22 600 - row titles. */
    val titleMd = s(16, 22, FontWeight.SemiBold, -0.005f)
    /** 14/20 400 - body copy. */
    val bodyMd = s(14, 20, FontWeight.Normal)
    /** 12/16 400 - row subtitles, legend entries. */
    val bodySm = s(12, 16, FontWeight.Normal, 0.01f)
    /** 10/14 0.06em 700 uppercase - every small caps label. */
    val labelCaps = s(10, 14, FontWeight.Bold, 0.06f)
    /** 16/22 600 - amounts in a list. */
    val numericMd = s(16, 22, FontWeight.SemiBold, -0.01f)
    /** 13/18 600 - the month line, "See all", stat figures. */
    val numericSm = s(13, 18, FontWeight.SemiBold)
}

@Composable
fun designType() = DesignType(jakarta())
