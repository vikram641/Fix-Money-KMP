package com.fixmoney.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.config.FeatureFlagRepository
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.expense.BudgetRepository
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.receipt.ExtractedReceipt
import com.fixmoney.ui.chat.ChatScreen
import com.fixmoney.ui.expense.ExpenseDetailScreen
import com.fixmoney.ui.settings.EditProfileScreen
import com.fixmoney.ui.settings.SipCalculatorScreen
import com.fixmoney.ui.expense.AddExpenseScreen
import com.fixmoney.ui.home.DashboardScreen
import com.fixmoney.ui.scanner.ReceiptScannerScreen
import com.fixmoney.ui.onboarding.*
import com.fixmoney.shared.coins.AiFeature
import com.fixmoney.shared.coins.WalletEvent
import com.fixmoney.shared.coins.WalletRepository
import com.fixmoney.ui.coins.CoinSheet
import com.fixmoney.ui.coins.PremiumState
import com.fixmoney.ui.coins.CoinSheetHost
import com.fixmoney.ui.coins.CoinsController
import com.fixmoney.ui.coins.LocalCoins
import com.fixmoney.ui.coins.WalletScreen
import com.fixmoney.ui.coins.rememberRewardedAdLauncher
import com.fixmoney.ui.coins.rememberPremiumStore
import com.fixmoney.ui.coins.confirmPremium
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** How long a horizontal destination change takes. */
private const val NAV_SLIDE_MS = 300

/**
 * Root composable, shared by both platforms. Android's MainActivity and iOS's
 * MainViewController both do nothing but host this.
 */
@Composable
fun App() {
    val appPrefs: AppPrefs = koinInject()
    val featureFlags: FeatureFlagRepository = koinInject()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    // One fetch per launch. Failure is silent by design - the last published
    // values (or all-on) stay in force, so a Firestore outage never blanks the
    // app out. See FeatureFlagRepository.
    LaunchedEffect(Unit) { featureFlags.refresh() }
    val flags = rememberFeatureFlags(featureFlags)
    // Settings' switch has to change the theme immediately, so the stored
    // preference is lifted into state here rather than read per-composition.
    // Null means "follow the system": a user who never touched the switch now
    // follows a system theme change mid-session instead of keeping the
    // launch-time value until the next process start.
    var darkPreference by remember { mutableStateOf(appPrefs.darkMode()) }
    val darkOverride = darkPreference ?: systemDark

    // Digit grouping (lakh vs thousand) follows the profile's currency.
    val profilePrefs: OnboardingPrefs = koinInject()
    remember(profilePrefs) {
        com.fixmoney.shared.model.MoneyFormat.currencyCode = { profilePrefs.getCurrencyCode() }
    }

    // The dark/light switch wipes in from the toggle rather than snapping.
    // Hosted here because it has to sit *outside* the theme to photograph the
    // old one. See ThemeReveal.kt.
    val themeReveal = rememberThemeRevealState()

    CompositionLocalProvider(
        LocalFeatureFlags provides flags,
        LocalThemeReveal provides themeReveal
    ) {
    ResponsiveUi {
    ThemeReveal(themeReveal) {
        FixMoneyTheme(darkTheme = darkOverride) {
            SystemBarsAppearance(
                darkTheme = darkOverride,
                navigationBarColor = Design.colors.surfaceNav
            )
            Surface(modifier = Modifier.fillMaxSize(), color = Design.colors.surface) {
            val prefs: OnboardingPrefs = koinInject()
            val budgets: BudgetRepository = koinInject()
            val categories: CategoryRepository = koinInject()
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()
            // Saveable: the nav back stack survives process death, so a plain
            // remember brought the user back to All Set with their answers reset
            // to "there", ₹25,000 and INR - and "Start tracking" saved those.
            val state = androidx.compose.runtime.saveable.rememberSaveable(
                saver = androidx.compose.runtime.saveable.listSaver(
                    save = { listOf(it.name, it.monthlyBudget.toString(), it.currencyCode, it.selectedCategoryIds.joinToString(",")) },
                    restore = { saved ->
                        OnboardingState(prefs, budgets, categories).apply {
                            name = saved[0]
                            monthlyBudget = saved[1].toIntOrNull() ?: 25_000
                            currencyCode = saved[2]
                            saved[3].split(",").filter { it.isNotBlank() }.forEach { selectedCategoryIds.add(it) }
                        }
                    }
                )
            ) { OnboardingState(prefs, budgets, categories) }

            // The scanner's result on its way back to Add Expense. The old app
            // used the nav back stack's savedStateHandle for this; holding it
            // here is the Compose equivalent and keeps the whole hop in common
            // code. Written by the scanner, consumed exactly once by Add
            // Expense - the same write-then-remove contract, so a second visit
            // to Add Expense never re-applies a stale scan.
            var pendingScan by remember { mutableStateOf<ExtractedReceipt?>(null) }

            // Which expense the detail screen is showing. Held here rather than
            // passed as a route argument: `NavBackStackEntry.arguments` is an
            // Android `Bundle` whose accessors do not exist in common code, so a
            // path argument compiles on Android and fails on iOS. Same pattern,
            // and same reason, as `pendingScan` above.
            var detailExpenseId by remember { mutableStateOf<String?>(null) }

            // Which expense Add Expense is editing, if any. Same state-held
            // pattern as `detailExpenseId`, and cleared on the way in so a new
            // expense never opens prefilled from a previous edit.
            var editingExpenseId by remember { mutableStateOf<String?>(null) }

            // "Skip" on any feature screen jumps to setupName, as the old app did.
            val skipToSetup: () -> Unit = { navController.navigate(Routes.SETUP_NAME) { launchSingleTop = true } }
            // Only pops when there is somewhere to go back to: a double tap on Back
            // during the exit animation used to pop Home itself and leave an empty
            // NavHost (a blank screen).
            val goBack: () -> Unit = {
                if (navController.previousBackStackEntry != null) navController.popBackStack()
            }
            // A double tap on any "open X" button pushed X twice.
            fun go(route: String) = navController.navigate(route) { launchSingleTop = true }

            // AI coins. One controller for the whole app so any screen refused
            // for coins can raise the sheet; the balance itself always comes
            // from the Worker via WalletRepository.
            val walletRepo: WalletRepository = koinInject()
            val adLauncher = rememberRewardedAdLauncher()
            val coins = remember { CoinsController(walletRepo, scope) }
            coins.ads = adLauncher
            // Fixoo Premium via Google Play. Any subscription Play still reports
            // (reinstall, new phone, a confirmation that never arrived) is
            // re-sent to the Worker quietly on launch.
            val premiumStore = rememberPremiumStore { token ->
                // If the "Payment pending" sheet is still up, this is that payment
                // completing - show the result there instead of silently.
                val pendingOnScreen = coins.sheet == CoinSheet.Premium(PremiumState.Pending)
                scope.launch { coins.confirmPremium(token, showSheet = pendingOnScreen) }
            }
            coins.store = premiumStore
            coins.openWallet = { navController.navigate(Routes.WALLET) { launchSingleTop = true } }
            // Collector first: the events flow does not replay, so a bonus
            // granted by the refresh below must find someone listening.
            LaunchedEffect(Unit) {
                walletRepo.events.collect { event ->
                    when (event) {
                        is WalletEvent.WelcomeBonus -> coins.sheet = CoinSheet.TrialEnded(event.coins)
                    }
                }
            }
            // A screen or sheet asked for from outside (the voice bubble).
            // Waits until the user is past splash and onboarding, where none
            // of these would make sense, then acts once. The sheets come up
            // over whatever screen is showing.
            val launchTarget by LaunchTarget.pending.collectAsState()
            LaunchedEffect(launchTarget) {
                if (launchTarget == null) return@LaunchedEffect
                navController.currentBackStackEntryFlow.first { it.destination.route != Routes.SPLASH }
                if (prefs.isOnboardingComplete()) {
                    when (launchTarget) {
                        // The coin_wallet flag hides the wallet; a deep link must not reopen it.
                        LaunchTarget.WALLET -> if (flags[com.fixmoney.shared.config.FeatureFlag.CoinWallet] != false) {
                            navController.navigate(Routes.WALLET) { launchSingleTop = true }
                        }
                        LaunchTarget.OUT_OF_COINS -> {
                            coins.sheet = CoinSheet.OutOfCoins
                            walletRepo.refresh()
                        }
                        LaunchTarget.TRIAL_LIMIT -> coins.sheet = CoinSheet.TrialLimit
                    }
                }
                LaunchTarget.consume()
            }
            LaunchedEffect(Unit) {
                // Registers on first launch and picks up the trial / welcome
                // bonus. Silent offline - every AI feature still works on the
                // old path until the Worker can be reached.
                walletRepo.refresh()
            }

            CompositionLocalProvider(LocalCoins provides coins) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                // Forward moves slide in from the right, back pops slide out to
                // it, so a swipe and a button produce the same motion. Set on
                // the NavHost rather than per-destination: every hop in this app
                // is a lateral one.
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        tween(NAV_SLIDE_MS)
                    ) + fadeIn(tween(NAV_SLIDE_MS))
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        tween(NAV_SLIDE_MS)
                    ) + fadeOut(tween(NAV_SLIDE_MS))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        tween(NAV_SLIDE_MS)
                    ) + fadeIn(tween(NAV_SLIDE_MS))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        tween(NAV_SLIDE_MS)
                    ) + fadeOut(tween(NAV_SLIDE_MS))
                }
            ) {
                composable(Routes.SPLASH) {
                    SplashScreen(
                        onDone = {
                            // Same gate the old SplashViewModel applied: an
                            // already-onboarded install skips onboarding. Read
                            // here rather than as a remembered start destination
                            // so completing onboarding in this session is seen.
                            val next = if (prefs.isOnboardingComplete()) Routes.HOME else Routes.WELCOME
                            navController.navigate(next) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.WELCOME) {
                    WelcomeScreen(onGetStarted = { navController.navigate(Routes.FEATURE_SCAN) { launchSingleTop = true } })
                }
                composable(Routes.FEATURE_SCAN) {
                    FeatureScreen(
                        page = FeaturePage.Scan,
                        onNext = { navController.navigate(Routes.FEATURE_BUDGETS) { launchSingleTop = true } },
                        onSkip = skipToSetup,
                        onBack = goBack
                    )
                }
                composable(Routes.FEATURE_BUDGETS) {
                    FeatureScreen(
                        page = FeaturePage.Budgets,
                        onNext = { navController.navigate(Routes.FEATURE_FIXOO) { launchSingleTop = true } },
                        onSkip = skipToSetup,
                        onBack = goBack
                    )
                }
                composable(Routes.FEATURE_FIXOO) {
                    FeatureScreen(
                        page = FeaturePage.Fixoo,
                        onNext = skipToSetup,
                        onSkip = skipToSetup,
                        onBack = goBack
                    )
                }
                composable(Routes.SETUP_NAME) {
                    SetupNameScreen(
                        state = state,
                        onBack = goBack,
                        onContinue = { navController.navigate(Routes.SETUP_BUDGET) { launchSingleTop = true } }
                    )
                }
                composable(Routes.SETUP_BUDGET) {
                    SetupBudgetScreen(
                        state = state,
                        onBack = goBack,
                        onContinue = { navController.navigate(Routes.SETUP_CURRENCY) { launchSingleTop = true } }
                    )
                }
                composable(Routes.SETUP_CURRENCY) {
                    SetupCurrencyScreen(
                        state = state,
                        onBack = goBack,
                        onFinish = { navController.navigate(Routes.ALL_SET) { launchSingleTop = true } }
                    )
                }
                composable(Routes.ALL_SET) {
                    AllSetScreen(
                        state = state,
                        onBack = goBack,
                        onStartTracking = {
                            // Only this screen commits the profile, exactly as
                            // AllSetFragment was the only caller of
                            // completeOnboarding(). It now also writes the
                            // budget rows, which is why it is a suspend call.
                            scope.launch { state.complete() }
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                                // A double tap pushed a second Home under the first.
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Routes.HOME) {
                    DashboardScreen(
                        onAddExpense = {
                            editingExpenseId = null
                            go(Routes.ADD_EXPENSE)
                        },
                        onAskAi = { go(Routes.CHAT) },
                        darkTheme = darkOverride,
                        onDarkModeChanged = { darkPreference = it },
                        onOpenSip = { go(Routes.SIP) },
                        onOpenProfile = { go(Routes.EDIT_PROFILE) },
                        onOpenExpense = {
                            detailExpenseId = it
                            go(Routes.EXPENSE_DETAIL)
                        }
                    )
                }
                composable(Routes.EDIT_PROFILE) {
                    EditProfileScreen(onBack = goBack)
                }
                composable(Routes.EXPENSE_DETAIL) {
                    ExpenseDetailScreen(
                        expenseId = detailExpenseId.orEmpty(),
                        onBack = goBack,
                        onEdit = { id ->
                            editingExpenseId = id
                            go(Routes.ADD_EXPENSE)
                        }
                    )
                }
                composable(Routes.SIP) {
                    SipCalculatorScreen(onBack = goBack)
                }
                composable(Routes.CHAT) {
                    ChatScreen(onBack = goBack)
                }
                composable(Routes.ADD_EXPENSE) {
                    AddExpenseScreen(
                        onClose = goBack,
                        // The coin precheck is a network round trip; if the user has
                        // left Add Expense by the time it answers, don't open the camera.
                        onScanReceipt = {
                            coins.proceedIf(AiFeature.Scan) {
                                if (navController.currentDestination?.route == Routes.ADD_EXPENSE) go(Routes.SCAN_RECEIPT)
                            }
                        },
                        scanned = pendingScan,
                        onScanConsumed = { pendingScan = null },
                        editingId = editingExpenseId
                    )
                }
                composable(Routes.SCAN_RECEIPT) {
                    ReceiptScannerScreen(
                        onClose = goBack,
                        onScanned = {
                            pendingScan = it
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.WALLET) {
                    WalletScreen(onBack = goBack)
                }
            }
            CoinSheetHost(coins)
            }
            }
        }
    }
    }
    }
}
