package com.fixmoney.ui.coins

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.fixmoney.shared.ai.AiEndpoint
import java.util.UUID

private const val TAG = "PremiumStore"

@Composable
actual fun rememberPremiumStore(onRestoredToken: (String) -> Unit): PremiumStore {
    val context = LocalContext.current
    val restored by rememberUpdatedState(onRestoredToken)
    val store = remember(context) { PlayPremiumStore(context.findActivity()) { restored(it) } }
    DisposableEffect(store) {
        store.connect()
        onDispose { store.disconnect() }
    }
    return store
}

/**
 * Google Play Billing 9. The account id is attached as `obfuscatedAccountId`,
 * so the Worker can see which wallet bought the subscription; the purchase
 * token goes to the Worker, which verifies and acknowledges it with Google.
 * Acknowledging is deliberately *not* done here - doing it on the device
 * would grant nothing and would let a tampered app skip the server check.
 */
private class PlayPremiumStore(
    private val activity: Activity?,
    private val onRestored: (String) -> Unit
) : PremiumStore {

    private val debuggable = activity != null &&
        (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private var details by mutableStateOf<ProductDetails?>(null)
    private var connected by mutableStateOf(false)
    private var pending: ((PurchaseResult) -> Unit)? = null
    private var started = false

    override val available: Boolean get() = details != null || simulated
    // Only against a local Worker: the production Worker rejects dev tokens, so
    // offering a "test purchase" there would just end in an error.
    override val simulated: Boolean
        get() = debuggable && connected && details == null && AiEndpoint.current != AiEndpoint.DEFAULT
    override val priceLabel: String?
        get() = details?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            ?: if (simulated) "₹99" else null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val cb = pending
        pending = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val bought = purchases.orEmpty().firstOrNull { PREMIUM_PRODUCT_ID in it.products }
                when (bought?.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> cb?.invoke(PurchaseResult.Purchased(bought.purchaseToken))
                        ?: onRestored(bought.purchaseToken) // a pending purchase that completed later
                    Purchase.PurchaseState.PENDING -> cb?.invoke(PurchaseResult.Pending)
                    else -> cb?.invoke(PurchaseResult.Failed("The purchase didn't complete."))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> cb?.invoke(PurchaseResult.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                cb?.invoke(PurchaseResult.Failed("You already have Fixoo Premium - restoring it."))
                restore()
            }
            else -> cb?.invoke(PurchaseResult.Failed(readable(result)))
        }
    }

    private val client: BillingClient? = activity?.let {
        BillingClient.newBuilder(it)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    fun connect() {
        val c = client ?: run { Log.w(TAG, "no activity - billing disabled"); return }
        // Not `c.isReady`: with enableAutoServiceReconnection() Billing 9 reports
        // ready before startConnection has ever been called, and skipping it on
        // that basis meant the product and owned purchases were never loaded.
        if (started) return
        started = true
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!connected) {
                    Log.w(TAG, "billing setup: ${result.responseCode} ${result.debugMessage}")
                    return
                }
                loadProduct()
                restore()
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    fun disconnect() {
        client?.endConnection()
        started = false
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client?.queryProductDetailsAsync(params) { result, queried ->
            details = queried.productDetailsList.firstOrNull()
            if (details == null) {
                // Expected for the `.kmp` debug package, which Play Console does not know.
                Log.w(TAG, "product $PREMIUM_PRODUCT_ID not available: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    /** Re-sends every owned subscription to the Worker: reinstall, new phone, or a lost confirmation. */
    fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && PREMIUM_PRODUCT_ID in it.products }
                .forEach { onRestored(it.purchaseToken) }
        }
    }

    override fun subscribe(accountId: String, onResult: (PurchaseResult) -> Unit) {
        val act = activity ?: return onResult(PurchaseResult.Failed("No screen to show Google Play on"))
        if (simulated) {
            // Debug only - see PremiumStore.simulated.
            onResult(PurchaseResult.Purchased("dev-premium-" + UUID.randomUUID()))
            return
        }
        val product = details ?: return onResult(PurchaseResult.Failed("Fixoo Premium isn't available right now. Try again later."))
        val offer = product.subscriptionOfferDetails?.firstOrNull()
            ?: return onResult(PurchaseResult.Failed("Fixoo Premium isn't available right now. Try again later."))
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            // Ties the purchase to this wallet; the Worker sees it as
            // obfuscatedExternalAccountId. An opaque id - no personal data.
            .setObfuscatedAccountId(accountId)
            .build()
        pending = onResult
        val result = client?.launchBillingFlow(act, flow)
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            pending = null
            onResult(PurchaseResult.Failed(result?.let { readable(it) } ?: "Google Play isn't available"))
        }
    }

    private fun readable(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play billing isn't available on this device."
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> "Couldn't reach Google Play. Check your connection."
        else -> "Google Play couldn't complete the purchase (${result.responseCode})."
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
