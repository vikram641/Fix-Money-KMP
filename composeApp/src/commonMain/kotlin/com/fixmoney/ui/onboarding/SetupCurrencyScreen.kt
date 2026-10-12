package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_check_circle
import com.fixmoney.resources.nd_rupee
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.horizontalSwipe
import com.fixmoney.ui.designType

/**
 * Step 3 of 3, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design 7/fixmoney_setup_choose_currency_obsidian_dark`).
 *
 * The mockup shows five currencies; the list stays at all 14, as the old screen
 * had. A mockup crops to what fits on one screen - it is not a statement that
 * the other nine should go.
 */
@Composable
fun SetupCurrencyScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val colors = Design.colors
    val t = designType()

    Column(
        modifier = Modifier.fillMaxSize()
            .horizontalSwipe(onSwipeLeft = onFinish, onSwipeRight = onBack)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        SetupHeader(step = 3, total = 3, onBack = onBack)

        Spacer(Modifier.height(32.dp))
        SetupIconTile(Res.drawable.nd_rupee)

        Spacer(Modifier.height(24.dp))
        Text("Choose your\ncurrency", style = t.displayHero, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "This is how amounts show up across the app.",
            style = t.bodyMd.copy(fontSize = 15.sp),
            color = colors.textMuted
        )

        Spacer(Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(CurrencyConstants.CURRENCIES, key = { it.code }) { currency ->
                val selected = currency.code == state.currencyCode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceCard)
                        .border(
                            // The selected row is outlined in the text colour,
                            // not the accent: the accent already carries the
                            // tick, and two purples on one row read as two
                            // different states.
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) colors.textPrimary else colors.stroke,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { state.currencyCode = currency.code }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Box(
                        modifier = Modifier.size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) colors.accent.copy(alpha = 0.18f)
                                else colors.surfaceHigh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            currency.symbol,
                            style = t.titleMd.copy(fontSize = 18.sp),
                            color = if (selected) colors.accent else colors.textMuted
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currency.name, style = t.titleMd, color = colors.textPrimary)
                        Text(currency.code, style = t.bodySm, color = colors.textMuted)
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                                .background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            LineIcon(Res.drawable.nd_check_circle, Color.White, 18.dp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        GlowButton(text = "Finish", onClick = onFinish, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))
    }
}
