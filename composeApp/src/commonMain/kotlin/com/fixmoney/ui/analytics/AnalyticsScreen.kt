package com.fixmoney.ui.analytics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.budget.shiftMonth
import com.fixmoney.ui.categoryIconFor
import com.fixmoney.ui.home.parseHexColor
import com.fixmoney.ui.onboarding.formatIndian
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

private val MONTH_LABELS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Port of `fragment_analytics.xml`: six months of spend as bars, then the
 * month's categories ranked. Bars are drawn as plain Boxes rather than a chart
 * library, for the same reason as Home's donut.
 */
@Composable
fun AnalyticsScreen() {
    val colors = AppTheme.colors
    val prefs: OnboardingPrefs = koinInject()
    val repo: ExpenseRepository = koinInject()

    val symbol = remember { CurrencyConstants.symbolFor(prefs.getCurrencyCode()) }
    val all by repo.observeExpenses().collectAsStateWithLifecycle(emptyList())

    val thisMonth = remember { currentMonthApi() }
    val months = remember(thisMonth) { (5 downTo 0).map { shiftMonth(thisMonth, -it) } }

    val perMonth = months.map { month ->
        month to all.filter { it.date.startsWith(month) }.sumOf { it.amount.toIntOrNull() ?: 0 }
    }
    val peak = perMonth.maxOfOrNull { it.second } ?: 0

    val thisMonthRows = all.filter { it.date.startsWith(thisMonth) }
    val topCategories = thisMonthRows
        .groupBy { it.categoryName }
        .map { (name, rows) ->
            Triple(
                name,
                rows.sumOf { it.amount.toIntOrNull() ?: 0 },
                rows.first().let { it.categoryColor to it.categoryIcon }
            )
        }
        .sortedByDescending { it.second }
    val topAmount = topCategories.firstOrNull()?.second ?: 0

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Analytics",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(prettyMonth(thisMonth), fontSize = 17.sp, color = colors.textSecondary)
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .border(1.dp, colors.cardStroke, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("MONTHLY SPENDING", fontSize = 14.sp, color = colors.textSecondary)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                perMonth.forEach { (_, amount) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val fraction = if (peak <= 0) 0f else amount.toFloat() / peak
                        Box(
                            Modifier
                                .width(30.dp)
                                .fillMaxHeight(fraction.coerceAtLeast(0.004f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accent)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                perMonth.forEach { (month, _) ->
                    Text(
                        MONTH_LABELS[(month.split("-")[1].toIntOrNull() ?: 1) - 1],
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("TOP CATEGORIES", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))

        if (topCategories.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Nothing spent this month yet", fontSize = 16.sp, color = colors.textSecondary)
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colors.cardStroke, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                topCategories.take(6).forEach { (name, amount, art) ->
                    val (colorHex, icon) = art
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(parseHexColor(colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(categoryIconFor(icon)),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$symbol${formatIndian(amount)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                                    .background(colors.progressBg)
                            ) {
                                val fraction = if (topAmount <= 0) 0f else amount.toFloat() / topAmount
                                Box(
                                    Modifier.fillMaxWidth(fraction).fillMaxHeight()
                                        .clip(CircleShape).background(parseHexColor(colorHex))
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** "2026-09" -> "September 2026". */
private fun prettyMonth(month: String): String {
    val parts = month.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val full = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    return "${full.getOrElse(m - 1) { month }} ${parts[0]}"
}
