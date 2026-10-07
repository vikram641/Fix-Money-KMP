package com.fixmoney.ui.coins

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fixmoney.shared.coins.WalletInfo
import com.fixmoney.ui.Design
import com.fixmoney.ui.designType
import com.fixmoney.shared.platform.currentTimeMillis

/**
 * The coin colour: the Stitch design system's own `tertiary` amber, used for
 * coins and nothing else so a coin reads as a coin on any screen. Kept here
 * rather than in `DesignColors` - it is one feature's accent, not a theme role.
 */
val CoinAmber = Color(0xFFFFBA4B)
val CoinAmberDeep = Color(0xFFC48400)
val OnCoin = Color(0xFF442B00)

/**
 * A flat amber coin with a four-point sparkle cut in - the AI hint. No currency
 * sign: coins are not money and must not look like it.
 */
@Composable
fun CoinGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.linearGradient(listOf(CoinAmber, CoinAmberDeep)), radius = r)
        drawCircle(Color.White.copy(alpha = 0.18f), radius = r * 0.78f, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.08f))
        val c = center
        val outer = r * 0.48f
        val inner = r * 0.14f
        val star = Path().apply {
            moveTo(c.x, c.y - outer)
            quadraticTo(c.x + inner, c.y - inner, c.x + outer, c.y)
            quadraticTo(c.x + inner, c.y + inner, c.x, c.y + outer)
            quadraticTo(c.x - inner, c.y + inner, c.x - outer, c.y)
            quadraticTo(c.x - inner, c.y - inner, c.x, c.y - outer)
            close()
        }
        drawPath(star, OnCoin)
        drawCircle(OnCoin, radius = r * 0.07f, center = Offset(c.x + outer * 0.9f, c.y - outer * 0.9f))
    }
}

/**
 * Compact balance pill. During the trial it shows the days left instead of a
 * number, since coins are not being spent yet.
 */
@Composable
fun CoinChip(wallet: WalletInfo?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Design.colors
    val t = designType()
    if (wallet?.premiumActive == true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.clip(CircleShape).background(colors.surfaceHigh).clickable(onClick = onClick)
                .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
        ) { PremiumBadge() }
        return
    }
    val label = when {
        wallet == null -> "–"
        wallet.trialActive -> "Trial · ${trialDaysLeft(wallet)}d"
        else -> wallet.coins.toString()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(CircleShape)
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
    ) {
        CoinGlyph(16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = t.numericSm, color = CoinAmber, maxLines = 1)
    }
}

/** "1 coin" / "Free" pill for an AI entry point. */
@Composable
fun CoinCostPill(cost: Int, modifier: Modifier = Modifier) {
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(CircleShape)
            .background(CoinAmber.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        CoinGlyph(11.dp)
        Spacer(Modifier.width(4.dp))
        Text(if (cost == 1) "1 coin" else "$cost coins", style = t.labelCaps, color = CoinAmber)
    }
}

/** Days left in the trial, rounded up so the last day reads "1", not "0". */
fun trialDaysLeft(wallet: WalletInfo): Int {
    val left = wallet.trialEndsAt - currentTimeMillis()
    if (left <= 0) return 0
    return ((left + 86_399_999L) / 86_400_000L).toInt()
}
