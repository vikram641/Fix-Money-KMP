package com.fixmoney.ui.coins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fixmoney.shared.coins.WalletInfo
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.GlowButton
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.ui.isEnabled

/**
 * Renders whichever coin sheet [CoinsController.sheet] names. Hosted once at
 * the app root so any screen can raise one.
 *
 * Every sheet tells the truth about money in the same few ways, because that is
 * the one place this feature could mislead: nothing is charged when the AI did
 * not answer, coins cannot be bought, and tracking expenses never costs coins.
 */
@Composable
fun CoinSheetHost(controller: CoinsController) {
    val sheet = controller.sheet ?: return
    val wallet by controller.repo.wallet.collectAsState()
    SheetShell(onDismiss = { controller.dismiss() }) {
        when (sheet) {
            CoinSheet.OutOfCoins -> OutOfCoins(controller, wallet)
            CoinSheet.TrialLimit -> TrialLimit(controller)
            is CoinSheet.TrialEnded -> TrialEnded(controller, sheet.bonus, wallet)
            is CoinSheet.Reward -> Reward(controller, sheet.state, wallet)
            CoinSheet.PremiumLimit -> PremiumLimit(controller)
            is CoinSheet.Premium -> PremiumFlow(controller, sheet.state)
        }
    }
}

/* ------------------------------------------------------------------ sheets */

@Composable
private fun ColumnScope.OutOfCoins(c: CoinsController, wallet: WalletInfo?) {
    val reward = wallet?.rewardPerAd ?: 5
    val adsLeft = wallet?.adsLeftToday ?: 0
    when {
        !c.videosSupported -> {
            Hero { CoinGlyph(56.dp) }
            Title("You're out of Fixoo Coins")
            Body("Video rewards aren't available on this device yet. Adding expenses by hand always works.")
            Spacer(Modifier.height(20.dp))
            GlowButton("OK", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
            PremiumAlternative(c)
        }
        adsLeft <= 0 -> {
            Hero { CoinGlyph(56.dp) }
            Title("You've used today's videos")
            Body("New videos unlock tomorrow. You can still add expenses by hand anytime.")
            Spacer(Modifier.height(20.dp))
            GlowButton("OK", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
            PremiumAlternative(c)
        }
        else -> {
            Hero { CoinGlyph(56.dp) }
            // "Out" would be wrong with a few coins left that just don't cover
            // the price (a feature can cost more than 1 - it is config).
            Title(if ((wallet?.coins ?: 0) > 0) "Not enough Fixoo Coins" else "You're out of Fixoo Coins")
            Body("Watch a short video to get $reward coins. Expense tracking stays free.")
            Spacer(Modifier.height(20.dp))
            GlowButton("Watch a video  +$reward", onClick = { c.watchVideo() }, modifier = Modifier.fillMaxWidth())
            PremiumAlternative(c)
            TextButton("Not now") { c.dismiss() }
            Muted("${adsLeft} of ${wallet?.dailyAdCap ?: 10} videos left today")
        }
    }
}

@Composable
private fun ColumnScope.TrialLimit(c: CoinsController) {
    Hero { CoinGlyph(56.dp) }
    Title("Today's free AI limit reached")
    Body("Your free trial covers a set number of AI uses each day, and it resets at midnight. Adding expenses by hand always works.")
    Spacer(Modifier.height(20.dp))
    GlowButton("OK", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
    PremiumAlternative(c)
}

@Composable
private fun ColumnScope.TrialEnded(c: CoinsController, bonus: Int, wallet: WalletInfo?) {
    val reward = wallet?.rewardPerAd ?: 5
    Hero { CoinGlyph(56.dp) }
    Title("Your free trial has ended")
    Body("Expense tracking is still free, forever. AI features now use coins - 1 coin per use, and every short video gives you $reward.")
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Step("Watch a video")
        Step("Get $reward coins")
        Step("Use any AI feature")
    }
    if (bonus > 0) {
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(CoinAmber.copy(alpha = 0.12f))
                .border(1.dp, CoinAmber.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            CoinGlyph(24.dp)
            Spacer(Modifier.width(10.dp))
            Text("Here are $bonus coins to start", style = designType().titleMd, color = Design.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("+$bonus", style = designType().titleMd, color = CoinAmber)
        }
    }
    Spacer(Modifier.height(16.dp))
    PlanCard(
        title = "Use free with coins",
        subtitle = "Watch a short video, earn coins, use any AI feature. Never charged to a card.",
        selected = true
    )
    if (isEnabled(FeatureFlag.PremiumPlan)) {
        Spacer(Modifier.height(10.dp))
        PremiumOfferCard(c.store) { c.subscribePremium() }
    }
    Spacer(Modifier.height(20.dp))
    GlowButton("Continue with coins (free)", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
    TextButton("See how coins work") { c.dismiss(); c.openWallet() }
}

@Composable
private fun PlanCard(title: String, subtitle: String, selected: Boolean) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceHigh)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) CoinAmber else colors.stroke, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        CoinGlyph(28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.titleMd, color = colors.textPrimary)
            Text(subtitle, style = t.bodySm, color = colors.textMuted)
        }
    }
}

@Composable
private fun ColumnScope.Reward(c: CoinsController, state: RewardState, wallet: WalletInfo?) {
    val reward = wallet?.rewardPerAd ?: 5
    val adsLeft = wallet?.adsLeftToday ?: 0
    when (state) {
        RewardState.Loading -> {
            Hero { CircularProgressIndicator(Modifier.size(40.dp), color = Design.colors.accent, strokeWidth = 3.dp) }
            Title("Loading video…")
            TextButton("Cancel") { c.dismiss() }
        }
        RewardState.Pending -> {
            Hero { CoinGlyph(64.dp) }
            Title("+$reward coins on the way…")
            Body("Confirming the video with the ad network. This usually takes a few seconds.")
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = CoinAmber, strokeWidth = 2.dp)
        }
        is RewardState.Credited -> {
            Hero { CoinGlyph(72.dp) }
            Title("Reward unlocked!")
            Body("You earned +${state.after - state.before} coins for watching a video.")
            Spacer(Modifier.height(16.dp))
            BalanceUpdate(state.before, state.after)
            Spacer(Modifier.height(12.dp))
            DailyLimit(wallet)
            Spacer(Modifier.height(20.dp))
            GlowButton("Done", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
            if (adsLeft > 0 && c.videosSupported) TextButton("Watch another  +$reward") { c.watchVideo() }
        }
        RewardState.Delayed -> {
            Hero { CoinGlyph(64.dp) }
            Title("Your coins are on the way")
            Body("The video finished, but confirmation is taking longer than usual. Your $reward coins will show up shortly - you don't need to watch again.")
            Spacer(Modifier.height(20.dp))
            GlowButton("OK", onClick = { c.dismiss(); c.refresh() }, modifier = Modifier.fillMaxWidth())
        }
        RewardState.ClosedEarly -> {
            Hero { CoinGlyph(56.dp) }
            Title("Video didn't finish")
            Body("No coins were added. Watch to the end to get $reward coins.")
            Spacer(Modifier.height(20.dp))
            GlowButton("Try again", onClick = { c.watchVideo() }, modifier = Modifier.fillMaxWidth())
            TextButton("Close") { c.dismiss() }
        }
        is RewardState.Failed -> {
            Hero { CoinGlyph(56.dp) }
            Title("No video right now")
            Body(state.message)
            Spacer(Modifier.height(20.dp))
            GlowButton("Try again", onClick = { c.watchVideo() }, modifier = Modifier.fillMaxWidth())
            TextButton("Close") { c.dismiss() }
        }
    }
}

@Composable
private fun ColumnScope.PremiumLimit(c: CoinsController) {
    Hero { CoinGlyph(56.dp) }
    Title("Today's fair-use limit reached")
    Body("Fixoo Premium includes a generous daily allowance of AI uses, and it resets at midnight. Adding expenses by hand always works.")
    Spacer(Modifier.height(20.dp))
    GlowButton("OK", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.PremiumFlow(c: CoinsController, state: PremiumState) {
    when (state) {
        PremiumState.Processing -> {
            Hero { CircularProgressIndicator(Modifier.size(40.dp), color = Design.colors.accent, strokeWidth = 3.dp) }
            Title("Confirming with Google Play…")
            Body("This takes a few seconds.")
        }
        PremiumState.Pending -> {
            Hero { CoinGlyph(56.dp) }
            Title("Payment pending")
            Body("Google Play is still confirming your payment - UPI can take a few minutes. Fixoo Premium starts as soon as it goes through; you don't need to pay again.")
            Spacer(Modifier.height(20.dp))
            GlowButton("OK", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
        }
        is PremiumState.Active -> {
            Hero { CoinGlyph(72.dp) }
            Title("Welcome to Fixoo Premium")
            Body(
                "AI features no longer use coins. " +
                    (if (state.renews) "Renews on ${premiumDate(state.until)}." else "Active until ${premiumDate(state.until)}.") +
                    " Cancel anytime in Google Play."
            )
            Spacer(Modifier.height(20.dp))
            GlowButton("Done", onClick = { c.dismiss() }, modifier = Modifier.fillMaxWidth())
        }
        is PremiumState.Failed -> {
            Hero { CoinGlyph(56.dp) }
            Title("Premium didn't start")
            Body(state.message)
            Spacer(Modifier.height(20.dp))
            GlowButton("Try again", onClick = { c.subscribePremium() }, modifier = Modifier.fillMaxWidth())
            TextButton("Close") { c.dismiss() }
        }
    }
}

/** "Or get Fixoo Premium" under the video button - only when Play can actually sell it. */
@Composable
private fun PremiumAlternative(c: CoinsController) {
    val store = c.store
    val price = store?.priceLabel
    if (store?.available != true || price == null || !isEnabled(FeatureFlag.PremiumPlan)) return
    TextButton("Or get Fixoo Premium · $price/month") { c.subscribePremium() }
}

/* ------------------------------------------------------------- shared bits */

@Composable
internal fun BalanceUpdate(before: Int, after: Int) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceHigh).padding(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Previous", style = t.bodySm, color = colors.textMuted)
            Text("$before", style = t.headlineSm, color = colors.textMuted)
        }
        Text("→", style = t.headlineSm, color = colors.textMuted)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("New total", style = t.bodySm, color = colors.textMuted)
            Text("$after", style = t.headlineLg, color = colors.textPrimary)
        }
    }
}

@Composable
internal fun DailyLimit(wallet: WalletInfo?) {
    val colors = Design.colors
    val t = designType()
    val cap = wallet?.dailyAdCap ?: 10
    val watched = (cap - (wallet?.adsLeftToday ?: cap)).coerceIn(0, cap)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceHigh).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily video limit", style = t.titleMd, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("$watched of $cap watched", style = t.numericSm, color = colors.accent)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(cap) { i ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(CircleShape)
                        .background(if (i < watched) colors.accent else colors.surface)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (watched >= cap) "Daily limit reached - resets at midnight" else "${cap - watched} videos left today · resets at midnight",
            style = t.bodySm, color = colors.textMuted
        )
    }
}

@Composable
private fun SheetShell(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = Design.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(CircleShape).background(colors.surfaceHigh))
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

@Composable
private fun Hero(content: @Composable () -> Unit) {
    Box(
        Modifier.size(112.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(CoinAmber.copy(alpha = 0.22f), Color.Transparent))),
        contentAlignment = Alignment.Center
    ) { content() }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Title(text: String) {
    Text(text, style = designType().headlineSm, color = Design.colors.textPrimary, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = designType().bodyMd, color = Design.colors.textMuted, textAlign = TextAlign.Center)
}

@Composable
private fun Muted(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = designType().bodySm, color = Design.colors.textMuted, textAlign = TextAlign.Center)
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Text(
        text,
        style = designType().titleMd,
        color = Design.colors.accent,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun Step(text: String, width: Dp = 96.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width)) {
        CoinGlyph(22.dp)
        Spacer(Modifier.height(6.dp))
        Text(text, style = designType().bodySm, color = Design.colors.textPrimary, textAlign = TextAlign.Center)
    }
}
