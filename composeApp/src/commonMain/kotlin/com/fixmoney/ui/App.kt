package com.fixmoney.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.ui.onboarding.SetupNameScreen
import com.fixmoney.ui.onboarding.WelcomeScreen
import org.koin.compose.koinInject

/**
 * Root composable, shared by both platforms. Android's MainActivity and iOS's
 * MainViewController both do nothing but host this.
 */
@Composable
fun App() {
    FixMoneyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val prefs: OnboardingPrefs = koinInject()
            val navController = rememberNavController()

            // Same gate the old app's SplashViewModel applied: an already-onboarded
            // install skips straight past onboarding.
            val start = remember { if (prefs.isOnboardingComplete()) Routes.HOME else Routes.WELCOME }

            NavHost(navController = navController, startDestination = start) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(onGetStarted = { navController.navigate(Routes.SETUP_NAME) })
                }
                composable(Routes.SETUP_NAME) {
                    SetupNameScreen(
                        onBack = { navController.popBackStack() },
                        onContinue = { name ->
                            // Budget and currency screens come next; for now this
                            // completes onboarding so persistence can be verified.
                            prefs.saveProfile(name, monthlyBudget = 0, currencyCode = "INR")
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
