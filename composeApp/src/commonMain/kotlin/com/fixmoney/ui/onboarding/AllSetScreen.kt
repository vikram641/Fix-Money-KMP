package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_arrow_right
import com.fixmoney.resources.nd_card
import com.fixmoney.resources.nd_check_circle
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.horizontalSwipe
import com.fixmoney.ui.designType
import org.jetbrains.compose.resources.DrawableResource

/**
 * The recap before entering the app, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design 7/fixmoney_setup_all_set_recap_obsidian_dark`).
 *
 * **The mockup's status strip is not shipped.** It reads "256-BIT ENCLAVE
 * READY" on the left and "SYNCED" on the right; this app encrypts nothing and
 * syncs nowhere - expenses live in a plain local Room database and never leave
 * the device. Both are replaced by what is true, and what Settings already
 * tells the user: stored on this device, offline.
 *
 * "Automatic cycle" under the budget row went the same way. Budgets are keyed
 * to the calendar month, so the row says that instead - the fiscal-cycle start
 * date is still a PREVIEW switch that nothing reads.
 */
@Composable
fun AllSetScreen(state: OnboardingState, onStartTracking: () -> Unit, onBack: () -> Unit = {}) {
    val colors = Design.colors
    val t = designType()
    val currency = CurrencyConstants.byCode(state.currencyCode)
    val displayName = state.name.trim().ifEmpty { "there" }

    Column(
        modifier = Modifier.fillMaxSize()
            // Right swipe goes back to the currency step. There is deliberately
            // no left swipe: "Start Tracking" commits the profile, and a commit
            // should take a real tap.
            .horizontalSwipe(onSwipeRight = onBack)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        SuccessMark()

        Spacer(Modifier.height(32.dp))
        Text(
            "You're all set, $displayName!",
            style = t.displayHero,
            textAlign = TextAlign.Center,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Your budget is ready to track. Here's a quick recap.",
            style = t.bodyMd.copy(fontSize = 15.sp, lineHeight = 23.sp),
            textAlign = TextAlign.Center,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
        ) {
            RecapRow(
                icon = Res.drawable.nd_card,
                label = "Monthly budget",
                sub = "Resets each calendar month",
                value = "${currency.symbol}${formatIndian(state.monthlyBudget)}"
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .height(HAIRLINE).background(colors.stroke)
            )
            RecapRow(
                symbolText = currency.symbol,
                label = "Currency",
                sub = currency.name,
                badge = currency.code
            )
            // Truthful counterpart to the mockup's encryption and sync claims -
            // see the note on this file.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .background(colors.surfaceHigh)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.positive))
                Spacer(Modifier.width(10.dp))
                Text("STORED ON THIS DEVICE", style = t.labelCaps, color = colors.textPrimary)
                Spacer(Modifier.weight(1f))
                Text("OFFLINE", style = t.labelCaps, color = colors.textMuted)
            }
        }

        Spacer(Modifier.weight(1f))
        GlowButton(
            text = "Start Tracking",
            onClick = onStartTracking,
            trailing = Res.drawable.nd_arrow_right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** The green tick, sitting inside a thin ring and its own bloom. */
@Composable
private fun SuccessMark() {
    val colors = Design.colors
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(210.dp).background(
                Brush.radialGradient(
                    0.0f to colors.positive.copy(alpha = 0.22f),
                    0.5f to colors.positive.copy(alpha = 0.07f),
                    1.0f to Color.Transparent
                )
            )
        )
        Box(
            Modifier.size(152.dp).clip(CircleShape)
                .border(1.dp, colors.positive.copy(alpha = 0.45f), CircleShape)
        )
        Box(
            Modifier.size(118.dp).clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(colors.positive, colors.positive.copy(alpha = 0.75f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Dark glyph on the bright disc, as the mockup has - a white tick
            // on this green is barely there.
            LineIcon(Res.drawable.nd_check_circle, colors.surface, 54.dp)
        }
    }
}

@Composable
private fun RecapRow(
    label: String,
    sub: String,
    value: String? = null,
    badge: String? = null,
    icon: DrawableResource? = null,
    symbolText: String? = null
) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(colors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                LineIcon(icon, colors.accent, 22.dp)
            } else {
                Text(
                    symbolText.orEmpty(),
                    style = t.titleMd.copy(fontSize = 18.sp),
                    color = colors.accent
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = t.titleMd, color = colors.textPrimary)
            Text(sub, style = t.bodySm, color = colors.textMuted)
        }
        when {
            value != null -> Text(
                value,
                style = t.numericMd.copy(fontSize = 19.sp),
                color = colors.textPrimary
            )
            badge != null -> Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHigh)
                    .border(1.dp, colors.stroke, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(badge, style = t.labelCaps, color = colors.textPrimary)
            }
        }
    }
}
