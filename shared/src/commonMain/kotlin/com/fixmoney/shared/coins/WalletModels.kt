package com.fixmoney.shared.coins

import kotlinx.serialization.Serializable

/**
 * The wallet as the Worker reports it. The app **displays** this and never
 * decides anything from it - charging and crediting happen server-side, so a
 * stale or tampered copy here can only ever show a wrong number, never spend a
 * coin that does not exist. Field names match `WalletView` in
 * `worker/src/wallet.ts`.
 */
@Serializable
data class WalletInfo(
    val coins: Int = 0,
    /** Coins minus anything held for a request still in flight. */
    val available: Int = 0,
    val trialActive: Boolean = false,
    val trialEndsAt: Long = 0L,
    val trialUsesLeftToday: Int = 0,
    val adsLeftToday: Int = 0,
    val dailyAdCap: Int = 10,
    val rewardPerAd: Int = 5,
    val costs: Map<String, Int> = emptyMap(),
    /** Present only on the response that granted it - drives the one-time "trial ended" sheet. */
    val welcomeBonusGranted: Int? = null,
    /** Fixoo Premium (Google Play subscription): AI uses cost no coins while true. */
    val premiumActive: Boolean = false,
    /** End of the paid period, from Google Play (epoch ms). */
    val premiumUntil: Long = 0L,
    /** False once the user has cancelled - premium still runs until [premiumUntil]. */
    val premiumAutoRenewing: Boolean = false,
    val premiumUsesLeftToday: Int = 0
) {
    fun cost(feature: AiFeature): Int = costs[feature.wire] ?: 1
}

/** The features that spend coins. [wire] is the `X-Feature` header value the Worker expects. */
enum class AiFeature(val wire: String, val label: String) {
    Fixoo("fixoo", "Fixoo AI chat"),
    Voice("voice", "Voice quick-add"),
    Scan("scan", "Receipt scan"),
    Insight("insight", "AI insight")
}

/**
 * How the coin layer answered an AI call, independent of what the model said.
 * Callers map this onto their own result types.
 */
enum class AiGate {
    /** Charged (or covered by the trial) and the model answered - or no wallet is in play. */
    Passed,
    /** Out of coins. Nothing was charged. */
    NoCoins,
    /** Trial's daily fair-use cap reached. Nothing was charged. */
    TrialLimit,
    /** Every AI provider is at capacity. Nothing was charged. */
    Busy,
    /** Fixoo Premium's daily fair-use cap is used up. Nothing was charged. */
    PremiumLimit
}

/**
 * One short line for a refused AI call, used where there is no room for the
 * full sheet - the voice bubble's toast, Siri's spoken reply, the scanner hint.
 * Every one says that nothing was charged, because that is the user's first
 * question.
 */
fun AiGate.shortMessage(): String = when (this) {
    AiGate.NoCoins -> "Out of Fixoo Coins - open FixMoney and watch a short video for more. Nothing was charged."
    AiGate.TrialLimit -> "You've used today's free AI limit. It resets tomorrow."
    AiGate.Busy -> "AI is busy right now - try again in about 30 seconds. No coin was used."
    AiGate.PremiumLimit -> "You've reached today's fair-use limit for Fixoo Premium. It resets at midnight."
    AiGate.Passed -> ""
}

/** Thrown out of the voice/receipt parsers so their existing failure paths don't swallow it. */
class AiBlockedException(val gate: AiGate) : Exception("AI call blocked: $gate")

/**
 * One line of the wallet's activity list, kept on this phone only.
 *
 * The Worker keeps no per-use ledger (by design - a Durable Object per wallet
 * holds a balance, not a history), so this is built from the balance changes
 * the app itself sees. It is a record of what happened *here*, not an audit:
 * a reinstall starts it empty, and a change the app never saw is not in it.
 */
@Serializable
data class CoinActivity(
    val at: Long,
    /** `yyyy-MM-dd` on the device when it happened, for "Today" / "Yesterday". */
    val day: String,
    val delta: Int,
    val label: String
)

sealed interface WalletEvent {
    /** The trial just ended and the Worker granted the one-time bonus. */
    data class WelcomeBonus(val coins: Int) : WalletEvent
}

@Serializable
internal data class RegisterRequest(val deviceKey: String, val platform: String)

@Serializable
internal data class RegisterResponse(val token: String, val accountId: String, val wallet: WalletInfo)

@Serializable
internal data class WalletResponse(val wallet: WalletInfo)

@Serializable
internal data class PremiumVerifyRequest(val purchaseToken: String)

/** Error bodies from the Worker; `wallet` rides along on 402 / 429. */
@Serializable
internal data class WorkerError(val error: String? = null, val code: String? = null, val wallet: WalletInfo? = null)
