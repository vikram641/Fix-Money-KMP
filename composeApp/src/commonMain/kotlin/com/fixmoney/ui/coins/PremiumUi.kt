package com.fixmoney.ui.coins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.fixmoney.shared.coins.WalletInfo
import com.fixmoney.shared.platform.epochDayToIso
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType
import com.fixmoney.ui.expense.prettyDate

/**
 * What premium gives, worded so nothing is promised that isn't true:
 * "no coins" rather than "unlimited" (there is a daily fair-use cap that
 * protects the shared AI quota), and no "ad-free" - the only ads in this app
 * are the opt-in videos, which a subscriber simply never needs.
 */
internal val PREMIUM_BENEFITS = listOf(
    "Fixoo AI, voice add and receipt scan without coins",
    "No videos to watch for coins",
    "Fair use: up to 100 AI uses a day"
)

/** Play's subscription page for this app, where users cancel or change payment. */
internal const val MANAGE_SUBSCRIPTION_URL =
    "https://play.google.com/store/account/subscriptions?sku=$PREMIUM_PRODUCT_ID&package=com.vikram641.expensetracker"

/** "12 November 2026"-style date for a Play expiry. India-first app, so the day boundary is IST. */
internal fun premiumDate(millis: Long): String =
    prettyDate(epochDayToIso((millis + 19_800_000L) / 86_400_000L))

/**
 * The buyable premium card (or "coming soon" when the store can't sell it -
 * iOS, or a device without Play). [onSubscribe] starts Google Play's checkout.
 */
@Composable
internal fun PremiumOfferCard(store: PremiumStore?, onSubscribe: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val price = store?.priceLabel
    val buyable = store?.available == true && price != null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(
                if (buyable) Brush.linearGradient(listOf(colors.heroStart.copy(alpha = 0.35f), colors.heroEnd.copy(alpha = 0.35f)))
                else Brush.linearGradient(listOf(colors.surfaceHigh.copy(alpha = 0.6f), colors.surfaceHigh.copy(alpha = 0.6f)))
            )
            .border(1.dp, if (buyable) colors.accentStrong else colors.stroke, RoundedCornerShape(18.dp))
            .then(if (buyable) Modifier.clickable(onClick = onSubscribe) else Modifier)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fixoo Premium", style = t.titleMd, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(colors.accent.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    if (buyable) "$price / month" else "COMING SOON",
                    style = if (buyable) t.numericSm else t.labelCaps,
                    color = colors.accent
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        PREMIUM_BENEFITS.forEach { Text("•  $it", style = t.bodySm, color = colors.textMuted) }
        if (buyable) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (store?.simulated == true) "Subscribe (test purchase - debug build)" else "Subscribe with Google Play",
                style = t.titleMd,
                color = colors.accent
            )
            Text(
                "Pay with UPI, Google Pay or card. Renews monthly - cancel anytime in Google Play.",
                style = t.bodySm,
                color = colors.textMuted
            )
        }
    }
}

/** Wallet's premium section: status when subscribed, the offer otherwise. */
@Composable
internal fun PremiumWalletCard(wallet: WalletInfo?, store: PremiumStore?, onSubscribe: () -> Unit) {
    if (wallet?.premiumActive != true) {
        PremiumOfferCard(store, onSubscribe)
        return
    }
    val colors = Design.colors
    val t = designType()
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.accentStrong, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fixoo Premium", style = t.titleMd, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(colors.positive.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) { Text("ACTIVE", style = t.labelCaps, color = colors.positive) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "AI features don't use coins. ${wallet.premiumUsesLeftToday} AI uses left today (fair use).",
            style = t.bodySm,
            color = colors.textMuted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (wallet.premiumAutoRenewing) "Renews on ${premiumDate(wallet.premiumUntil)}"
            else "Cancelled - premium ends on ${premiumDate(wallet.premiumUntil)}",
            style = t.bodySm,
            color = colors.textMuted
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Manage subscription",
            style = t.titleMd,
            color = colors.accent,
            modifier = Modifier.clip(CircleShape).clickable { uri.openUri(MANAGE_SUBSCRIPTION_URL) }
                .padding(vertical = 6.dp)
        )
    }
}

/** Small "Premium" pill used in place of the coin balance. */
@Composable
internal fun PremiumBadge() {
    val t = designType()
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoinGlyph(16.dp)
        Spacer(Modifier.width(6.dp))
        Text("Premium", style = t.numericSm, color = CoinAmber, maxLines = 1)
    }
}
