package com.fixmoney.ui.coins

import androidx.compose.runtime.Composable

/** Product id of the Rs 99/month subscription in Play Console. Must match worker `PREMIUM_PRODUCT_ID`. */
const val PREMIUM_PRODUCT_ID = "fixoo_premium_monthly"

/** How one purchase attempt ended, as far as the device can tell. */
sealed interface PurchaseResult {
    /** Play says it's paid. The token still has to be verified by the Worker before premium starts. */
    data class Purchased(val purchaseToken: String) : PurchaseResult
    /** UPI and some other methods can take a while; Play will call back when it completes. */
    data object Pending : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/**
 * Fixoo Premium through the platform store - Google Play Billing on Android
 * (UPI / Google Pay / cards at Play's checkout). Nothing here grants premium:
 * every token goes to the Worker, which verifies it with Google.
 *
 * iOS: not supported yet (StoreKit needs the Apple Developer account).
 */
interface PremiumStore {
    /** True once the store answered and the product exists, so it can be bought. */
    val available: Boolean
    /** The localized price exactly as the store shows it, e.g. "₹99.00", or null until known. */
    val priceLabel: String?
    /**
     * Debug builds only, when the real product is unavailable (the `.kmp`
     * debug package is not in Play Console): purchases are simulated with a
     * `dev-premium-*` token that only a DEV_MODE local Worker accepts. A
     * release build, or the real Worker, can never be fooled by it.
     */
    val simulated: Boolean
    fun subscribe(accountId: String, onResult: (PurchaseResult) -> Unit)
}

/**
 * [onRestoredToken] receives every subscription Play still reports as owned
 * when the store connects - a reinstall, a new phone, or a purchase whose
 * confirmation never reached the Worker. Each is re-sent for verification.
 */
@Composable
expect fun rememberPremiumStore(onRestoredToken: (String) -> Unit): PremiumStore
