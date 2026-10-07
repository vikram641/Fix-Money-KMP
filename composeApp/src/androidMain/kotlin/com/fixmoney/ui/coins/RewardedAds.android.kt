package com.fixmoney.ui.coins

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

private const val TAG = "RewardedAds"

/** Google's public test unit - always fills, never pays, safe to tap. */
private const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

/**
 * The real rewarded unit, "Fixoo Coins Rewarded" in the AdMob console (created
 * 2026-10-07): reward 5 "FixooCoins", server-side verification pointed at the
 * Worker's /v1/admob/ssv and verified there. Its numeric part must stay in the
 * Worker's `REWARDED_AD_UNITS`, or every callback is refused. The reward item
 * has no space on purpose - see the comment in worker/src/ssv.ts.
 *
 * **Debug builds always use Google's test unit, never this one.** The old app
 * learned that the hard way: the OnePlus Nord's AdMob test-device hash changed
 * between sessions, and a *real* ad was served to the developer's own phone
 * despite registration - self-served real ads are invalid traffic and can get
 * the AdMob account suspended. The cost: Google's test unit has no SSV callback,
 * so in a debug build the video plays and completes but **no coins arrive**.
 * Test crediting server-side instead (worker/test, or a signed callback sent to
 * a staging Worker).
 */
private const val REAL_REWARDED_UNIT = "ca-app-pub-7178541936408370/8131398118"

@Composable
actual fun rememberRewardedAdLauncher(): RewardedAdLauncher {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val launcher = remember(activity) { AndroidRewardedAds(activity) }
    DisposableEffect(launcher) {
        launcher.prepare()
        onDispose { }
    }
    return launcher
}

private class AndroidRewardedAds(private val activity: Activity?) : RewardedAdLauncher {

    private val debuggable = activity != null &&
        (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val unitId = if (debuggable) TEST_REWARDED_UNIT else REAL_REWARDED_UNIT

    override val supported: Boolean = activity != null && unitId.isNotEmpty()

    private var loaded: RewardedAd? = null
    private var loading = false
    private var ready = false
    /** A show() that arrived before an ad was loaded; served as soon as one is. */
    private var pending: Pair<String, (AdResult) -> Unit>? = null

    /**
     * Consent first, then the SDK, then one ad preloaded so the tap plays at
     * once. The UMP form only appears where the law requires it (EEA/UK); a
     * consent failure must never block the app, so it carries on regardless -
     * the SDK itself then serves only what consent allows.
     */
    fun prepare() {
        val act = activity ?: return
        if (!supported || ready) return
        val info = UserMessagingPlatform.getConsentInformation(act)
        info.requestConsentInfoUpdate(
            act,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(act) { formError ->
                    formError?.let { Log.w(TAG, "consent form: ${it.message}") }
                    startSdk(act)
                }
            },
            { error ->
                Log.w(TAG, "consent update failed: ${error.message}")
                startSdk(act)
            }
        )
    }

    private fun startSdk(act: Activity) {
        if (ready) return
        ready = true
        if (debuggable) {
            // Defence in depth only - see REAL_REWARDED_UNIT for why debug builds
            // do not rely on this. Same two hashes the old app registered.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("5ECA94DDBA24462E82FE5C3DB1B8B82A", "6268EABA190A8CCA3761DF1ED4AF0037"))
                    .build()
            )
        }
        MobileAds.initialize(act) { load(act) }
    }

    private fun load(act: Activity) {
        if (loading || loaded != null) return
        loading = true
        RewardedAd.load(act, unitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loading = false
                loaded = ad
                pending?.let { (id, cb) -> pending = null; show(id, cb) }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                Log.w(TAG, "load failed: ${error.code} ${error.message}")
                pending?.let { (_, cb) ->
                    pending = null
                    cb(AdResult.Failed("No video available right now. Try again in a minute."))
                }
            }
        })
    }

    override fun show(accountId: String, onResult: (AdResult) -> Unit) {
        val act = activity ?: return onResult(AdResult.Failed("No screen to show the video on"))
        if (!supported) return onResult(AdResult.Failed("Video rewards are not available yet"))
        val ad = loaded
        if (ad == null) {
            pending = accountId to onResult
            if (!ready) prepare() else load(act)
            return
        }
        loaded = null

        // The only thing that ties this view to a wallet. AdMob echoes it back
        // as `user_id` in the server-side callback, and the Worker credits that
        // account - the app itself never reports a reward.
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder().setUserId(accountId).build()
        )

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onResult(if (earned) AdResult.Earned else AdResult.ClosedEarly)
                load(act) // have the next one ready
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "show failed: ${error.message}")
                onResult(AdResult.Failed("The video couldn't play. Try again."))
                load(act)
            }
        }
        ad.show(act) { earned = true }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
