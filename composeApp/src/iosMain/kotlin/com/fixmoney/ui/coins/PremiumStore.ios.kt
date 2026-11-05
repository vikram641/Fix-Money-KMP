package com.fixmoney.ui.coins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** No StoreKit yet - it needs the Apple Developer account. The premium card stays "coming soon" on iOS. */
@Composable
actual fun rememberPremiumStore(onRestoredToken: (String) -> Unit): PremiumStore = remember {
    object : PremiumStore {
        override val available = false
        override val priceLabel: String? = null
        override val simulated = false
        override fun subscribe(accountId: String, onResult: (PurchaseResult) -> Unit) {
            onResult(PurchaseResult.Failed("Fixoo Premium isn't available on iOS yet"))
        }
    }
}
