package com.fixmoney.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_fixmoney_logo
import com.fixmoney.resources.nd_arrow_right
import com.fixmoney.resources.nd_budget
import com.fixmoney.resources.nd_scan_doc
import com.fixmoney.resources.nd_wallet
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.horizontalSwipe
import com.fixmoney.ui.designType
import org.jetbrains.compose.resources.painterResource

/**
 * The first screen, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design (6)`).
 *
 * **The mockup's security claims are not shipped.** It badges the header
 * "Encrypted" and offers a "Zero-Knowledge Vault" chip; this app stores
 * expenses in a plain local Room database, and there is no encryption anywhere
 * in `shared/`. A claim like that on the very first screen is the kind a user
 * would reasonably rely on, so it is replaced by what is actually true and
 * already says so in Settings: the data is on this device, and it works
 * offline.
 *
 * "Auto Budget Caps" went the same way - budgets are real, nothing about them
 * is automatic - and the hero tile's "ACTIVE PULSE" status line went with it,
 * because a live-looking status dot implies an account and a connection that do
 * not exist. The tile keeps the card artwork and carries the product line
 * instead.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val colors = Design.colors
    val t = designType()

    Column(
        modifier = Modifier.fillMaxSize()
            // Swipe left to go on, exactly as "Get Started" does. There is
            // nothing behind this screen, so the right swipe is left null.
            .horizontalSwipe(onSwipeLeft = onGetStarted)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                // White, not `accent`: the mark is itself two shades of purple,
                // so a purple plate swallows it. Same plate as the splash.
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(Color.White)
                    .border(1.dp, colors.stroke, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_fixmoney_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textPrimary)) { append("Fix") }
                    withStyle(SpanStyle(color = colors.accent)) { append("Money") }
                },
                style = t.headlineSm.copy(fontSize = 22.sp)
            )
            Spacer(Modifier.weight(1f))
            // Truthful counterpart to the mockup's "Encrypted" badge - see the
            // note on this file.
            TagPill(text = "On this device", icon = Res.drawable.nd_wallet)
        }

        Spacer(Modifier.weight(1f))

        HeroCardTile(modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(40.dp))
        Text(
            "Take control of your money",
            style = t.displayHero,
            textAlign = TextAlign.Center,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Track expenses, scan receipts and set budget caps - all in one place.",
            style = t.bodyMd.copy(fontSize = 15.sp, lineHeight = 24.sp),
            textAlign = TextAlign.Center,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TagPill(text = "AI Receipt Parser", icon = Res.drawable.nd_scan_doc)
            Spacer(Modifier.width(10.dp))
            TagPill(text = "Works Offline", dot = colors.positive)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TagPill(text = "Category Budgets", icon = Res.drawable.nd_budget)
        }

        Spacer(Modifier.weight(1f))

        GlowButton(
            text = "Get Started",
            onClick = onGetStarted,
            trailing = Res.drawable.nd_arrow_right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        // Plain text, not links: the mockup underlines both, and there are no
        // Terms or Privacy Policy screens or URLs in this build to send anyone
        // to. An underlined phrase that does nothing when tapped is worse than
        // one that never looked tappable.
        Text(
            "By continuing you agree to our Terms & Privacy Policy.",
            style = t.bodySm,
            textAlign = TextAlign.Center,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The mockup's hero: a payment card floating on a glowing plate.
 *
 * Deliberately **not** a real card - no number, no name, no balance. It is a
 * product illustration on a screen shown before the app has any data at all,
 * and inventing a balance here is the one thing this app must never do.
 */
@Composable
private fun HeroCardTile(modifier: Modifier = Modifier) {
    val colors = Design.colors
    val t = designType()
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(300.dp).background(
                Brush.radialGradient(
                    0.0f to colors.accent.copy(alpha = 0.26f),
                    0.45f to colors.accent.copy(alpha = 0.10f),
                    1.0f to Color.Transparent
                )
            )
        )
        Column(
            modifier = Modifier.size(206.dp)
                .clip(RoundedCornerShape(46.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(46.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.size(width = 142.dp, height = 90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Chip.
                    Box(
                        Modifier.size(width = 26.dp, height = 18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF0B429))
                    )
                    Spacer(Modifier.weight(1f))
                    LineIcon(Res.drawable.nd_wallet, Color.White.copy(alpha = 0.85f), 15.dp)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(width = 58.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.55f))
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "EVERY RUPEE, TRACKED",
                style = t.labelCaps,
                color = colors.textMuted
            )
        }
    }
}
