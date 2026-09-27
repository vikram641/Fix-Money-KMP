package com.fixmoney.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_wallet_empty
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.shared.home.HomeStateFactory
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.onboarding.formatIndian
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Port of `fragment_home.xml` + `HomeFragment`/`HomeViewModel`, in its empty
 * state - there is no expense data source yet (Room), so spend is 0 and the
 * "No expenses yet" card shows, which is what a fresh install saw before too.
 *
 * Not yet ported: the AI insight card, the pie chart + legend, the "Recent"
 * list, the offline banner, pull-to-refresh, the month picker, and the AdMob
 * banner. Each needs a data source or a library that does not exist here yet.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun HomeScreen(onAddExpense: () -> Unit = {}, onAskAi: () -> Unit = {}) {
    val colors = AppTheme.colors
    val factory: HomeStateFactory = koinInject()
    var month by remember { mutableStateOf(currentMonthApi()) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // The old Home had this as a LottieAnimationView ("Ask AI" button).
    val aiComposition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(
            Res.readBytes("files/lottie/ai.json").decodeToString()
        )
    }
    // remember(month) so switching months restarts the flow from a matching
    // initial value instead of briefly showing the previous month's totals.
    val flow = remember(month) { factory.observe(month) }
    val state by flow.collectAsStateWithLifecycle(remember(month) { factory.build(month = month) })

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.greeting,
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.month,
                        fontSize = 15.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.clickable { showMonthPicker = true }
                    )
                }
                Image(
                    painter = rememberLottiePainter(
                        composition = aiComposition,
                        iterations = Compottie.IterateForever
                    ),
                    contentDescription = "Ask AI",
                    modifier = Modifier.size(52.dp).clickable(onClick = onAskAi)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.initials,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            BudgetCard(state)

            Spacer(Modifier.height(18.dp))
            if (state.hasExpenses) {
                SpendingDonut(state.breakdown)
            } else {
                EmptyStateCard(onAddExpense)
            }

            Spacer(Modifier.height(110.dp))
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.accent)
                .clickable(onClick = onAddExpense),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }

    if (showMonthPicker) {
        MonthYearDialog(
            title = "Select Month  Year",
            initial = month,
            showDays = false,
            onDismiss = { showMonthPicker = false },
            onConfirm = { month = it; showMonthPicker = false }
        )
    }
}

@Composable
private fun BudgetCard(state: com.fixmoney.shared.home.HomeUiState) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bgCard)
            .border(1.dp, colors.cardStroke, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Text(
            "TOTAL SPENT THIS MONTH",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "${state.currencySymbol} ${formatIndian(state.totalSpent)}",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(colors.progressBg)
        ) {
            if (state.percentUsed > 0) {
                Box(
                    Modifier.fillMaxWidth(state.percentUsed / 100f).fillMaxHeight()
                        .clip(CircleShape).background(colors.accent)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.percentUsed}% to ${state.totalBudget}",
            fontSize = 14.sp,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatTile(
                label = "This Week",
                value = "${state.currencySymbol}${formatIndian(state.weekSpent)}",
                tint = colors.green,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Remaining",
                value = "${state.currencySymbol} ${formatIndian(state.remaining)}",
                tint = colors.accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardStroke, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 15.sp, color = tint)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun EmptyStateCard(onAddExpense: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.bgCard)
            .border(1.dp, colors.cardStroke, RoundedCornerShape(22.dp))
            .padding(vertical = 30.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_wallet_empty),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "No expenses yet",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Log your first expense and this space turns into your spending breakdown.",
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.85f))
                .clickable(onClick = onAddExpense)
                .padding(horizontal = 26.dp, vertical = 14.dp)
        ) {
            Text(
                "+ Add your first expense",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
