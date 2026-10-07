package com.fixmoney.ui.coins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * No ads on iOS yet: it needs the Google Mobile Ads Swift package in Xcode, an
 * iOS app in the AdMob console, and an App Tracking Transparency decision - all
 * blocked on the Apple Developer account. The wallet hides the video button
 * while this is false.
 */
@Composable
actual fun rememberRewardedAdLauncher(): RewardedAdLauncher = remember {
    object : RewardedAdLauncher {
        override val supported = false
        override fun show(accountId: String, onResult: (AdResult) -> Unit) {
            onResult(AdResult.Failed("Video rewards are not available on iOS yet"))
        }
    }
}
