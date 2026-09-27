package com.fixmoney.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.ui.chat.ChatScreen
import com.fixmoney.ui.expense.AddExpenseScreen
import com.fixmoney.ui.home.DashboardScreen
import com.fixmoney.ui.onboarding.*
import org.koin.compose.koinInject

/**
 * Root composable, shared by both platforms. Android's MainActivity and iOS's
 * MainViewController both do nothing but host this.
 */
@Composable
fun App() {
    val appPrefs: AppPrefs = koinInject()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    // Settings' switch has to change the theme immediately, so the stored
    // preference is lifted into state here rather than read per-composition.
    var darkOverride by remember { mutableStateOf(appPrefs.darkMode() ?: systemDark) }

    FixMoneyTheme(darkTheme = darkOverride) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val prefs: OnboardingPrefs = koinInject()
            val navController = rememberNavController()
            val state = remember { OnboardingState(prefs) }

            // Same gate the old SplashViewModel applied: an already-onboarded
            // install skips straight past onboarding.
            val start = remember { if (prefs.isOnboardingComplete()) Routes.HOME else Routes.WELCOME }

            // "Skip" on any feature screen jumps to setupName, as the old app did.
            val skipToSetup: () -> Unit = { navController.navigate(Routes.SETUP_NAME) { launchSingleTop = true } }

            NavHost(navController = navController, startDestination = start) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(onGetStarted = { navController.navigate(Routes.FEATURE_SCAN) { launchSingleTop = true } })
                }
                composable(Routes.FEATURE_SCAN) {
                    FeatureScreen(
                        page = FeaturePage.Scan,
                        onNext = { navController.navigate(Routes.FEATURE_AUTODEBIT) { launchSingleTop = true } },
                        onSkip = skipToSetup
                    )
                }
                composable(Routes.FEATURE_AUTODEBIT) {
                    FeatureScreen(
                        page = FeaturePage.AutoDebit,
                        onNext = { navController.navigate(Routes.FEATURE_ALERTS) { launchSingleTop = true } },
                        onSkip = skipToSetup
                    )
                }
                composable(Routes.FEATURE_ALERTS) {
                    FeatureScreen(
                        page = FeaturePage.Alerts,
                        onNext = skipToSetup,
                        onSkip = skipToSetup
                    )
                }
                composable(Routes.SETUP_NAME) {
                    SetupNameScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onContinue = { navController.navigate(Routes.SETUP_BUDGET) { launchSingleTop = true } }
                    )
                }
                composable(Routes.SETUP_BUDGET) {
                    SetupBudgetScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onContinue = { navController.navigate(Routes.SETUP_CURRENCY) { launchSingleTop = true } }
                    )
                }
                composable(Routes.SETUP_CURRENCY) {
                    SetupCurrencyScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onFinish = { navController.navigate(Routes.ALL_SET) { launchSingleTop = true } }
                    )
                }
                composable(Routes.ALL_SET) {
                    AllSetScreen(
                        state = state,
                        onStartTracking = {
                            // Only this screen commits the profile, exactly as
                            // AllSetFragment was the only caller of completeOnboarding().
                            state.complete()
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.HOME) {
                    DashboardScreen(
                        onAddExpense = { navController.navigate(Routes.ADD_EXPENSE) },
                        onAskAi = { navController.navigate(Routes.CHAT) },
                        onDarkModeChanged = { darkOverride = it }
                    )
                }
                composable(Routes.CHAT) {
                    ChatScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.ADD_EXPENSE) {
                    AddExpenseScreen(onClose = { navController.popBackStack() })
                }
            }
        }
    }
}
