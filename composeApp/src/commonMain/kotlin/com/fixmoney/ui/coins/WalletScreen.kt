package com.fixmoney.ui.coins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fixmoney.resources.*
import com.fixmoney.shared.coins.AiFeature
import com.fixmoney.shared.coins.CoinActivity
import com.fixmoney.shared.platform.plusDaysIso
import com.fixmoney.shared.platform.timeLabelOf
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.shared.coins.WalletInfo
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.GlowButton
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.ui.isEnabled
import org.jetbrains.compose.resources.DrawableResource

/**
 * The AI Coins wallet, from the Stitch `fixmoney_coin_wallet` mockup.
 *
 * **Most of that mockup is not here, deliberately.** It showed daily quests,
 * expense streaks, a weekly budget target and a referral bonus as ways to earn
 * coins, prices of 5-15 coins per feature, coins for exports, and a recent
 * activity list of audits and OCR scans. None of those earning paths exist -
 * the Worker credits coins only from verified rewarded videos - exports are
 * free, and every AI feature costs what `wrangler.jsonc` says (1 by default).
 * A wallet that advertises ways to earn that do not work is the fastest way to
 * make the real one look broken.
 *
 * **Recent activity is this phone's own record**, not the mockup's list of
 * audits and scans: the Worker keeps no per-use ledger (by design - see
 * worker/README.md), so `WalletRepository` notes each balance change it sees.
 * A reinstall starts it empty, and the card says the list is from this phone.
 */
@Composable
fun WalletScreen(onBack: () -> Unit) {
    val controller = LocalCoins.current ?: return
    val colors = Design.colors
    val t = designType()
    val wallet by controller.repo.wallet.collectAsState()
    val activity by controller.repo.activity.collectAsState()

    LaunchedEffect(Unit) { controller.repo.refresh() }

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceCard)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_arrow_left, colors.textPrimary, 19.dp)
            }
            Spacer(Modifier.width(14.dp))
            Text("Fixoo Coins", style = t.headlineSm, color = colors.textPrimary, modifier = Modifier.weight(1f))
            CoinChip(wallet, onClick = {})
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            BalanceHero(wallet)
            if (isEnabled(FeatureFlag.PremiumPlan) || wallet?.premiumActive == true) {
                PremiumWalletCard(wallet, controller.store) { controller.subscribePremium() }
            }
            if (wallet?.premiumActive != true) EarnCard(controller, wallet)
            UnlocksCard(wallet)
            ActivityCard(activity)
            Text(
                "Coins stay on this phone. They can't be bought and have no cash value.",
                style = t.bodySm,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BalanceHero(wallet: WalletInfo?) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("AVAILABLE COINS", style = t.labelCaps, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoinGlyph(40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                wallet?.coins?.toString() ?: "–",
                style = t.displayHero,
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text("coins", style = t.bodyMd, color = Color.White.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(6.dp))
        // The prices are server config, so the line follows them rather than
        // promising "1 coin = 1 AI use" when a feature costs more.
        val prices = wallet?.let { w -> AiFeature.entries.filter { it != AiFeature.Insight }.map { w.cost(it) }.toSet() }
        Text(
            when {
                prices == null || prices == setOf(1) -> "1 coin = 1 AI use"
                prices.size == 1 -> "${prices.first()} coins = 1 AI use"
                else -> "Each AI feature lists its price below"
            },
            style = t.bodySm,
            color = Color.White.copy(alpha = 0.8f)
        )

        if (wallet == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Couldn't reach FixMoney to load your coins. Check your connection.",
                style = t.bodySm,
                color = Color.White.copy(alpha = 0.85f)
            )
        } else if (wallet.trialActive) {
            val days = trialDaysLeft(wallet)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "Free trial · $days ${if (days == 1) "day" else "days"} left",
                    style = t.numericSm,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "All AI features are free until then (${wallet.trialUsesLeftToday} uses left today).",
                style = t.bodySm,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun EarnCard(controller: CoinsController, wallet: WalletInfo?) {
    val colors = Design.colors
    val t = designType()
    val reward = wallet?.rewardPerAd ?: 5
    val adsLeft = wallet?.adsLeftToday ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceCard).padding(16.dp)
    ) {
        Text("Earn coins", style = t.titleMd, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Watch a short video to the end and get $reward coins.",
            style = t.bodySm,
            color = colors.textMuted
        )
        Spacer(Modifier.height(12.dp))
        when {
            !controller.videosSupported -> Text(
                "Video rewards aren't available on this device yet.",
                style = t.bodyMd,
                color = colors.textMuted
            )
            else -> {
                GlowButton(
                    text = if (wallet != null && adsLeft <= 0) "Daily limit reached - resets tomorrow" else "Watch a video  +$reward",
                    onClick = { controller.watchVideo() },
                    enabled = wallet != null && adsLeft > 0,
                    modifier = Modifier.fillMaxWidth()
                )
                // Only with a real wallet: "0 of 10 watched" before the Worker
                // has answered would be a made-up number.
                if (wallet != null) {
                    Spacer(Modifier.height(4.dp))
                    DailyLimit(wallet)
                }
            }
        }
    }
}

@Composable
private fun UnlocksCard(wallet: WalletInfo?) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceCard).padding(16.dp)
    ) {
        Text("What coins unlock", style = t.titleMd, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        UnlockRow(Res.drawable.nd_sparkle_duo, "Fixoo AI chat", "Ask anything about your spending", wallet, AiFeature.Fixoo)
        UnlockRow(Res.drawable.nd_mic, "Voice quick-add", "Say \"150 coffee\" and it's saved", wallet, AiFeature.Voice)
        UnlockRow(Res.drawable.nd_scan_doc, "Receipt scan", "Photo to expense in seconds", wallet, AiFeature.Scan)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Res.drawable.nd_check_circle, colors.positive, 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Expense tracking, budgets, analytics and export are always free.",
                style = t.bodySm,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun UnlockRow(
    icon: DrawableResource,
    title: String,
    subtitle: String,
    wallet: WalletInfo?,
    feature: AiFeature
) {
    val colors = Design.colors
    val t = designType()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { LineIcon(icon, colors.accent, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.titleMd, color = colors.textPrimary)
            Text(subtitle, style = t.bodySm, color = colors.textMuted)
        }
        CoinCostPill(wallet?.cost(feature) ?: 1)
    }
}

private const val ACTIVITY_SHOWN = 10

@Composable
private fun ActivityCard(activity: List<CoinActivity>) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceCard).padding(16.dp)
    ) {
        Text("Recent activity", style = t.titleMd, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        if (activity.isEmpty()) {
            Text(
                "Coins you earn and spend on this phone will show here. Uses during the free trial cost nothing, so they don't appear.",
                style = t.bodySm,
                color = colors.textMuted
            )
            return@Column
        }
        activity.take(ACTIVITY_SHOWN).forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(entry.label, style = t.titleMd, color = colors.textPrimary)
                    Text(whenLabel(entry), style = t.bodySm, color = colors.textMuted)
                }
                Text(
                    if (entry.delta > 0) "+${entry.delta}" else "${entry.delta}",
                    style = t.numericSm,
                    color = if (entry.delta > 0) colors.positive else colors.textPrimary
                )
            }
        }
    }
}

/** "Today, 9:26 AM", "Yesterday, 6:02 PM", or "Oct 3, 6:02 PM". */
private fun whenLabel(entry: CoinActivity): String {
    val today = todayIso()
    val time = timeLabelOf(entry.at)
    return when (entry.day) {
        today -> "Today, $time"
        plusDaysIso(today, -1) -> "Yesterday, $time"
        else -> "${prettyDay(entry.day)}, $time"
    }
}

private fun prettyDay(iso: String): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val parts = iso.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { months.getOrNull(it - 1) } ?: return iso
    return "$month ${parts.getOrNull(2)?.toIntOrNull() ?: ""}"
}
