package com.fixmoney.ui.coins

import androidx.compose.runtime.Composable

/** How one rewarded video ended, as far as the device can tell. */
sealed interface AdResult {
    /** Watched to the end. The coins themselves arrive via AdMob's server callback, not from here. */
    data object Earned : AdResult
    /** Closed before the reward point. Nothing is credited. */
    data object ClosedEarly : AdResult
    /** No ad could be loaded or shown. */
    data class Failed(val message: String) : AdResult
}

/**
 * Shows rewarded videos. The device is never trusted with the reward: [show]
 * tags the ad with the wallet's account id, AdMob calls the Worker when the
 * video completes, and the Worker credits the coins. [AdResult.Earned] only
 * means "go and wait for the balance to move".
 *
 * Android: Google Mobile Ads + the UMP consent form. iOS: [supported] is false
 * until the Google Mobile Ads Swift package and an iOS AdMob app exist.
 */
interface RewardedAdLauncher {
    val supported: Boolean
    fun show(accountId: String, onResult: (AdResult) -> Unit)
}

@Composable
expect fun rememberRewardedAdLauncher(): RewardedAdLauncher
