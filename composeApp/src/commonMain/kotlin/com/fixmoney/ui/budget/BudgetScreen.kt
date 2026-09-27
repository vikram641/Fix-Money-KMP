package com.fixmoney.ui.budget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.expense.BudgetRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.categoryIconFor
import com.fixmoney.ui.home.parseHexColor
import com.fixmoney.ui.onboarding.formatIndian
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Port of `fragment_budget.xml`: a month stepper, the overall roll-up, and a
 * per-category row with its own progress bar and a delete action.
 *
 * "+ Set Budget" splits the profile's monthly figure across every category,
 * which is what onboarding's budget step did. The old app also had a dedicated
 * per-category editor; that screen is not migrated.
 */
@Composable
fun BudgetScreen() {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val prefs: OnboardingPrefs = koinInject()
    val budgets: BudgetRepository = koinInject()
    val categories: CategoryRepository = koinInject()

    val symbol = remember { CurrencyConstants.symbolFor(prefs.getCurrencyCode()) }
    var month by remember { mutableStateOf(currentMonthApi()) }
    val flow = remember(month) { budgets.observeForMonth(month) }
    val rows by flow.collectAsStateWithLifecycle(emptyList())

    val totalLimit = rows.sumOf { it.limit }
    val totalSpent = rows.sumOf { it.spent }
    val overallPercent = if (totalLimit <= 0) 0 else ((totalSpent * 100L) / totalLimit).toInt()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Budget",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "+ Set Budget",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier.clickable {
                    scope.launch {
                        budgets.setSplitBudget(
                            categories = categories.getCategories(),
                            total = prefs.getMonthlyBudget(),
                            month = month,
                            currency = prefs.getCurrencyCode()
                        )
                    }
                }
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colors.textSecondary,
                modifier = Modifier.clickable { month = shiftMonth(month, -1) }.padding(8.dp)
            )
            Text(
                month,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "›",
                fontSize = 26.sp,
                color = colors.textSecondary,
                modifier = Modifier.clickable { month = shiftMonth(month, 1) }.padding(8.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .border(1.dp, colors.cardStroke, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row {
                Text(
                    "Overall Budget",
                    fontSize = 17.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$overallPercent% used",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.badgeWarningText
                )
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(overallPercent, colors.accent)
            Spacer(Modifier.height(10.dp))
            Text(
                "$symbol${formatIndian(totalSpent)} spent of $symbol${formatIndian(totalLimit)}",
                fontSize = 15.sp,
                color = colors.textSecondary
            )
        }

        Spacer(Modifier.height(18.dp))
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "No budgets for this month.\nTap “+ Set Budget” to split your monthly figure.",
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = colors.textSecondary
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(rows, key = { it.id }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(colors.bgCard)
                            .border(1.dp, colors.cardStroke, RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                .background(parseHexColor(row.categoryColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(categoryIconFor(row.categoryIcon)),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.categoryName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$symbol${row.spent}/$symbol${row.limit}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.badgeWarningText
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "🗑",
                                    fontSize = 17.sp,
                                    modifier = Modifier.clickable {
                                        scope.launch { budgets.delete(row.id) }
                                    }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            ProgressBar(row.percent, parseHexColor(row.categoryColor))
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Int, color: androidx.compose.ui.graphics.Color) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.progressBg)) {
        if (percent > 0) {
            Box(
                Modifier.fillMaxWidth(percent / 100f).fillMaxHeight()
                    .clip(CircleShape).background(color)
            )
        }
    }
}

/** "2026-09" shifted by whole months, wrapping the year. */
internal fun shiftMonth(month: String, delta: Int): String {
    val parts = month.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return month
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return month
    val total = year * 12 + (m - 1) + delta
    return "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
}
