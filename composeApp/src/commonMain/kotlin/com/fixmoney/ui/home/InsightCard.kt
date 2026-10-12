package com.fixmoney.ui.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.shared.home.HomeInsight
import com.fixmoney.ui.AppTheme

/**
 * Home's "AI INSIGHT" card - the old `bg_ai_insight_card.xml` plus its content,
 * rebuilt as a composable.
 *
 * The name is the old app's and is a little generous: nothing here is
 * generated, it is all arithmetic over local rows (see `HomeInsightEngine`).
 * That is the point - it costs no API quota and cannot hallucinate a number.
 *
 * The card is only composed when there is an insight at all; the engine returns
 * null rather than fill the space with something vacuous.
 */
@Composable
fun InsightCard(insight: HomeInsight) {
    val colors = AppTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(colors.aiInsightBgStart, colors.aiInsightBgEnd))
            )
            .border(1.dp, colors.aiInsightStroke, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦", fontSize = 15.sp, color = colors.accent)
            Spacer(Modifier.width(8.dp))
            Text(
                "AI INSIGHT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = colors.accent
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            headline(insight),
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        subline(insight)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 15.sp, lineHeight = 21.sp, color = colors.textSecondary)
        }

        // The forecast bar: how far through the month we are, then the stretch
        // from here to the projected overage in the warning colour. No bar at
        // all when the insight is a category trend only - there is nothing to
        // plot, and an empty track reads as a broken widget.
        val overage = insight.overageFraction
        if (overage != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    .background(colors.progressBg)
            ) {
                Box(
                    Modifier.fillMaxWidth(overage.coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(CircleShape).background(colors.textWarning)
                )
                Box(
                    Modifier
                        .fillMaxWidth(insight.monthProgressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.accent)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", fontSize = 13.sp, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    "Projected overage · ${monthAbbrev(insight)} ${insight.projectedOverageDay}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textWarning
                )
            }
        }
    }
}

private fun headline(insight: HomeInsight): String = when {
    insight.hasForecast -> "You're on track to exceed your budget this month"
    else -> "${insight.categoryName} spending is up ${insight.categoryPercentChange}% this week"
}

private fun subline(insight: HomeInsight): String? = when {
    // When both signals fired, the forecast is the headline and the category
    // trend becomes the supporting line - the old card's own ordering.
    insight.hasForecast && insight.hasCategoryTrend ->
        "${insight.categoryName} is up ${insight.categoryPercentChange}% on your 3-week average"
    insight.hasForecast -> "On track to exceed budget by ${monthAbbrev(insight)} ${insight.projectedOverageDay} at this pace"
    else -> "Compared with your 3-week average"
}

/** The card only ever describes the month in progress, so the label is today's. */
private fun monthAbbrev(insight: HomeInsight): String {
    val names = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val month = com.fixmoney.shared.platform.todayIso().split("-").getOrNull(1)?.toIntOrNull()
    return names.getOrNull((month ?: 1) - 1) ?: ""
}
