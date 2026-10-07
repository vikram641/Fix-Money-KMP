package com.fixmoney.ui.coins

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.fixmoney.shared.coins.AiFeature
import com.fixmoney.shared.coins.AiGate
import com.fixmoney.shared.coins.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which coin sheet, if any, is over the app. One at a time. */
sealed interface CoinSheet {
    /** An AI feature was refused for lack of coins. */
    data object OutOfCoins : CoinSheet
    /** The trial's daily fair-use cap is used up. */
    data object TrialLimit : CoinSheet
    /** First open after the trial ended; [bonus] coins were just granted. */
    data class TrialEnded(val bonus: Int) : CoinSheet
    data class Reward(val state: RewardState) : CoinSheet
    /** Fixoo Premium's daily fair-use cap is used up. */
    data object PremiumLimit : CoinSheet
    data class Premium(val state: PremiumState) : CoinSheet
}

/** Buying Fixoo Premium, step by step. */
sealed interface PremiumState {
    /** Google Play's sheet is up, or the Worker is verifying the token. */
    data object Processing : PremiumState
    /** UPI and some methods confirm later; premium starts when Play says it's paid. */
    data object Pending : PremiumState
    data class Active(val until: Long, val renews: Boolean) : PremiumState
    data class Failed(val message: String) : PremiumState
}

/** The rewarded-video flow, step by step. */
sealed interface RewardState {
    data object Loading : RewardState
    /** Video finished; waiting for AdMob's server callback to reach the Worker. */
    data object Pending : RewardState
    data class Credited(val before: Int, val after: Int) : RewardState
    /** Finished, but the callback has not landed yet. The coins arrive on a later refresh. */
    data object Delayed : RewardState
    data object ClosedEarly : RewardState
    data class Failed(val message: String) : RewardState
}

/**
 * The coin UI's one piece of shared state: which sheet is up, and the
 * rewarded-video flow. Created once in `App.kt` and reached anywhere through
 * [LocalCoins], so a screen that gets refused for coins can raise the sheet
 * without every screen in between threading callbacks through.
 *
 * It never decides a balance. Coins come from [WalletRepository], which only
 * ever reports what the Worker said.
 */
@Stable
class CoinsController(
    val repo: WalletRepository,
    internal val scope: CoroutineScope
) {
    var sheet by mutableStateOf<CoinSheet?>(null)

    /** Set by `App.kt` to its navigation call. */
    var openWallet: () -> Unit = {}

    /** Set by `App.kt` from [rememberRewardedAdLauncher]. */
    var ads: RewardedAdLauncher? = null

    /** Set by `App.kt` from [rememberPremiumStore]. */
    var store: PremiumStore? = null

    val videosSupported: Boolean get() = ads?.supported == true

    fun dismiss() {
        sheet = null
    }

    /** Raise the right sheet for a refused AI call. Busy is shown in place by the caller. */
    fun onBlocked(gate: AiGate) {
        when (gate) {
            AiGate.NoCoins -> sheet = CoinSheet.OutOfCoins
            AiGate.TrialLimit -> sheet = CoinSheet.TrialLimit
            AiGate.PremiumLimit -> sheet = CoinSheet.PremiumLimit
            AiGate.Busy, AiGate.Passed -> Unit
        }
    }

    fun refresh() {
        scope.launch { repo.refresh() }
    }

    /**
     * Runs [action] unless the Worker would refuse [feature] for lack of coins,
     * in which case the out-of-coins sheet comes up instead. Used before the
     * user puts effort in - opening the camera for a scan - so they are not told
     * after framing and shooting a receipt. See [WalletRepository.precheck].
     */
    fun proceedIf(feature: AiFeature, action: () -> Unit) {
        scope.launch {
            val gate = repo.precheck(feature)
            if (gate == AiGate.Passed) action() else onBlocked(gate)
        }
    }

    /**
     * Plays one rewarded video. The app never credits anything: the ad is
     * tagged with the account id, AdMob tells the Worker, and this only waits
     * for the balance to move.
     */
    fun watchVideo() {
        val launcher = ads ?: return
        val before = repo.wallet.value?.coins ?: 0
        sheet = CoinSheet.Reward(RewardState.Loading)
        scope.launch {
            val account = repo.accountId ?: run { repo.ensureRegistered(); repo.accountId }
            if (account == null) {
                sheet = CoinSheet.Reward(RewardState.Failed("Couldn't reach FixMoney. Check your connection and try again."))
                return@launch
            }
            launcher.show(account) { result ->
                when (result) {
                    AdResult.Earned -> {
                        sheet = CoinSheet.Reward(RewardState.Pending)
                        scope.launch {
                            val landed = repo.awaitReward(before)
                            val after = repo.wallet.value?.coins ?: before
                            // Only replace the sheet if the user is still looking at it.
                            if (sheet == CoinSheet.Reward(RewardState.Pending)) {
                                sheet = CoinSheet.Reward(
                                    if (landed) RewardState.Credited(before, after) else RewardState.Delayed
                                )
                            }
                        }
                    }
                    AdResult.ClosedEarly -> sheet = CoinSheet.Reward(RewardState.ClosedEarly)
                    is AdResult.Failed -> sheet = CoinSheet.Reward(RewardState.Failed(result.message))
                }
            }
        }
    }
}

/**
 * Starts a Fixoo Premium purchase. Google Play takes the payment (UPI / Google
 * Pay / card); the token then goes to the Worker, which verifies it with Google
 * before premium starts. Nothing is granted on the device.
 */
fun CoinsController.subscribePremium() {
    val s = store ?: return
    sheet = CoinSheet.Premium(PremiumState.Processing)
    scope.launch {
        val account = repo.accountId ?: run { repo.ensureRegistered(); repo.accountId }
        if (account == null) {
            sheet = CoinSheet.Premium(PremiumState.Failed("Couldn't reach FixMoney. Check your connection and try again."))
            return@launch
        }
        s.subscribe(account) { result ->
            when (result) {
                is PurchaseResult.Purchased -> scope.launch { confirmPremium(result.purchaseToken, showSheet = true) }
                PurchaseResult.Pending -> sheet = CoinSheet.Premium(PremiumState.Pending)
                PurchaseResult.Cancelled -> sheet = null
                is PurchaseResult.Failed -> sheet = CoinSheet.Premium(PremiumState.Failed(result.message))
            }
        }
    }
}

/**
 * Hands a Play purchase token to the Worker. Used after a purchase (with the
 * sheet) and for restores on launch (silently - the balance chip just turns
 * into "Premium").
 */
suspend fun CoinsController.confirmPremium(purchaseToken: String, showSheet: Boolean) {
    if (showSheet) sheet = CoinSheet.Premium(PremiumState.Processing)
    val result = repo.verifyPremium(purchaseToken)
    if (!showSheet) return
    sheet = when (result) {
        is WalletRepository.PremiumResult.Active ->
            CoinSheet.Premium(PremiumState.Active(result.wallet.premiumUntil, result.wallet.premiumAutoRenewing))
        WalletRepository.PremiumResult.Inactive ->
            CoinSheet.Premium(PremiumState.Failed("Google Play says this subscription isn't active yet. If you paid by UPI, it can take a few minutes."))
        is WalletRepository.PremiumResult.Failed -> CoinSheet.Premium(PremiumState.Failed(result.message))
    }
}

/** Null only in previews and before `App.kt` provides it. */
val LocalCoins = staticCompositionLocalOf<CoinsController?> { null }
