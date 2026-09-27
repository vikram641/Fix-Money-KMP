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
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.ui.onboarding.*
import org.koin.compose.koinInject

/**
 * Root composable, shared by both platforms. Android's MainActivity and iOS's
 * MainViewController both do nothing but host this.
 */
@Composable
fun App() {
    FixMoneyTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val prefs: OnboardingPrefs = koinInject()
            val navController = rememberNavController()
            val state = remember { OnboardingState(prefs) }

            // Same gate the old SplashViewModel applied: an already-onboarded
            // install skips straight past onboarding.
            val start = remember { if (prefs.isOnboardingComplete()) Routes.HOME else Routes.WELCOME }

            // "Skip" on any feature screen jumps to setupName, as the old app did.
            val skipToSetup: () -> Unit = { navController.navigate(Routes.SETUP_NAME) }

            NavHost(navController = navController, startDestination = start) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(onGetStarted = { navController.navigate(Routes.FEATURE_SCAN) })
                }
                composable(Routes.FEATURE_SCAN) {
                    FeatureScreen(
                        page = FeaturePage.Scan,
                        onNext = { navController.navigate(Routes.FEATURE_AUTODEBIT) },
                        onSkip = skipToSetup
                    )
                }
                composable(Routes.FEATURE_AUTODEBIT) {
                    FeatureScreen(
                        page = FeaturePage.AutoDebit,
                        onNext = { navController.navigate(Routes.FEATURE_ALERTS) },
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
                        onContinue = { navController.navigate(Routes.SETUP_BUDGET) }
                    )
                }
                composable(Routes.SETUP_BUDGET) {
                    SetupBudgetScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onContinue = { navController.navigate(Routes.SETUP_CURRENCY) }
                    )
                }
                composable(Routes.SETUP_CURRENCY) {
                    SetupCurrencyScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onFinish = { navController.navigate(Routes.ALL_SET) }
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
                composable(Routes.HOME) { HomePlaceholderScreen() }
            }
        }
    }
}

/** Proves at a glance which platform the shared UI is actually running on. */
expect fun platformName(): String
