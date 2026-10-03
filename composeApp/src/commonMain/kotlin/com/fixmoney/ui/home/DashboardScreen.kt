package com.fixmoney.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.isEnabled
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType
import com.fixmoney.ui.analytics.AnalyticsScreen
import com.fixmoney.ui.budget.BudgetScreen
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.horizontalSwipe
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
    Home("Home", Res.drawable.nd_home),
    Expenses("Expenses", Res.drawable.nd_expenses),
    Analytics("Analytics", Res.drawable.nd_analytics),
    Budget("Budget", Res.drawable.nd_budget),
    Settings("Settings", Res.drawable.nd_settings)
}

/** How long a tab change takes. */
private const val TAB_SLIDE_MS = 260

@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit = {},
    onAskAi: () -> Unit = {},
    darkTheme: Boolean = true,
    onDarkModeChanged: (Boolean) -> Unit = {},
    onOpenSip: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenExpense: (String) -> Unit = {}
) {
    val colors = AppTheme.colors
    val design = Design.colors
    val t = designType()
    // A tab the flags have switched off disappears from the bar *and* from the
    // swipe order, so swiping never lands on a screen that is not offered.
    val tabs = DashboardTab.entries.filter { entry ->
        when (entry) {
            DashboardTab.Analytics -> isEnabled(FeatureFlag.AnalyticsTab)
            DashboardTab.Budget -> isEnabled(FeatureFlag.BudgetTab)
            else -> true
        }
    }
    var tab by remember { mutableStateOf(DashboardTab.Home) }

    // The selected tab can vanish under the user when a refresh lands mid-use.
    LaunchedEffect(tabs) {
        if (tab !in tabs) tab = DashboardTab.Home
    }

    // Swiping moves one tab along; the tabs are a flat row, so it clamps at
    // both ends rather than wrapping - wrapping from Settings back to Home
    // reads as a mis-tap, not navigation.
    val step: (Int) -> Unit = { delta ->
        // Indexed into the *visible* list, not the enum - with a tab hidden the
        // two no longer line up.
        val current = tabs.indexOf(tab).coerceAtLeast(0)
        val next = (current + delta).coerceIn(0, tabs.lastIndex)
        if (next != current) tab = tabs[next]
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f)
                .background(design.surface)
                .horizontalSwipe(onSwipeLeft = { step(1) }, onSwipeRight = { step(-1) })
        ) {
            // The slide direction comes from which way the index moved, so a
            // swipe and a tab tap both push the new screen in from the side it
            // actually lives on. `targetState.ordinal > initialState.ordinal`
            // is read inside the transform, where both are in scope.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = tabs.indexOf(targetState) > tabs.indexOf(initialState)
                    val width = { full: Int -> if (forward) full else -full }
                    (slideInHorizontally(tween(TAB_SLIDE_MS)) { width(it) } +
                        fadeIn(tween(TAB_SLIDE_MS))) togetherWith
                        (slideOutHorizontally(tween(TAB_SLIDE_MS)) { -width(it) } +
                            fadeOut(tween(TAB_SLIDE_MS))) using
                        // Without this the container animates between the two
                        // screens' heights and every tab change jolts vertically.
                        SizeTransform(clip = false)
                },
                label = "tab"
            ) { current ->
                when (current) {
                    DashboardTab.Home -> HomeScreen(
                        onAddExpense = onAddExpense,
                        onAskAi = onAskAi,
                        onOpenProfile = onOpenProfile,
                        onSeeAllExpenses = { tab = DashboardTab.Expenses },
                        onOpenExpense = onOpenExpense
                    )
                    DashboardTab.Expenses -> ExpensesScreen(
                        onOpenExpense = onOpenExpense,
                        onAddExpense = onAddExpense
                    )
                    DashboardTab.Budget -> BudgetScreen()
                    DashboardTab.Settings -> SettingsScreen(
                        darkTheme = darkTheme,
                        onDarkModeChanged = onDarkModeChanged,
                        onOpenSip = onOpenSip,
                        onOpenProfile = onOpenProfile
                    )
                    DashboardTab.Analytics -> AnalyticsScreen(onAskAi = onAskAi)
                }
            }
        }

        // The 2026 nav bar: a darker-than-page slab with a hairline on top, a
        // tinted pill behind the active tab, and stroke icons tinted by state.
        // The old bar used the full-colour PNG icons, which cannot be tinted and
        // so could not show selection at all except through the label.
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(design.stroke))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Background before the inset, so the bar extends under the
                    // system navigation bar rather than leaving a strip of page.
                    .background(design.surfaceNav)
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { item ->
                    val selected = item == tab
                    val tint = if (selected) design.accent else design.textMuted
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (selected) design.accent.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable { tab = item }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        LineIcon(item.icon, tint, 22.dp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            style = t.labelCaps,
                            color = tint
                        )
                    }
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
