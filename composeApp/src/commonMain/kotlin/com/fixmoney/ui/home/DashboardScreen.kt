package com.fixmoney.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.*
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.analytics.AnalyticsScreen
import com.fixmoney.ui.budget.BudgetScreen
import com.fixmoney.ui.expense.ExpensesScreen
import com.fixmoney.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The five-tab shell from the old `nav_graph.xml` / `bottom_nav_menu.xml`,
 * hosting Home. Only Home is built; the other four are placeholders until
 * their features are migrated.
 */
enum class DashboardTab(val label: String, val icon: DrawableResource) {
    Home("Home", Res.drawable.img_home),
    Expenses("Expenses", Res.drawable.exp),
    Analytics("Analytics", Res.drawable.ic_any),
    Budget("Budget", Res.drawable.ic_bug),
    Settings("Settings", Res.drawable.ic_set)
}

@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit = {},
    onAskAi: () -> Unit = {},
    onDarkModeChanged: (Boolean) -> Unit = {},
    onOpenSip: () -> Unit = {}
) {
    val colors = AppTheme.colors
    var tab by remember { mutableStateOf(DashboardTab.Home) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                DashboardTab.Home -> HomeScreen(onAddExpense = onAddExpense, onAskAi = onAskAi)
                DashboardTab.Expenses -> ExpensesScreen()
                DashboardTab.Budget -> BudgetScreen()
                DashboardTab.Settings -> SettingsScreen(
                    onDarkModeChanged = onDarkModeChanged,
                    onOpenSip = onOpenSip
                )
                DashboardTab.Analytics -> AnalyticsScreen()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Not `bottomNavBg`: that token is #FFFFFF in colors.xml with no
                // values-night override, so it stayed white in dark mode.
                .background(colors.bgCard)
                // Only the navigation-bar inset. `safeDrawingPadding()` applies
                // every inset including the status bar's, which showed up as a
                // large empty gap above these icons.
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardTab.entries.forEach { item ->
                val selected = item == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { tab = item }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) colors.accent.copy(alpha = 0.22f) else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    ) {
                        Image(
                            painter = painterResource(item.icon),
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) colors.accent else colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingSoon(label: String) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Not migrated yet",
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = colors.textSecondary
        )
    }
}
