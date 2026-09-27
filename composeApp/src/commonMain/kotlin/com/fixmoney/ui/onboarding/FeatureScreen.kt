package com.fixmoney.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_autodebit
import com.fixmoney.resources.ic_bell_outline
import com.fixmoney.resources.ic_receipt_scan
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.IconBadge
import org.jetbrains.compose.resources.DrawableResource

/**
 * Port of `fragment_onboarding_feature.xml` + `OnboardingFeatureFragment`.
 * The old app reused one Fragment for all three screens, with content supplied
 * as nav-graph `<argument>` defaults; [FeaturePage] is the direct equivalent.
 */
enum class FeaturePage(
    val icon: DrawableResource,
    val title: String,
    val subtitle: String,
    val buttonText: String,
    val animation: String
) {
    Scan(
        Res.drawable.ic_receipt_scan,
        "Scan receipts in seconds",
        "Snap a photo and AI instantly fills in the amount, date, merchant, and category for you.",
        "Next",
        "scan"
    ),
    AutoDebit(
        Res.drawable.ic_autodebit,
        "Never miss a due date",
        "Track recurring bills and auto-debit payments — we'll remind you before they're due.",
        "Next",
        "none"
    ),
    Alerts(
        Res.drawable.ic_bell_outline,
        "Smart alerts, right on time",
        "Get notified when you're close to a budget limit or a bill is coming up.",
        "Let's go",
        "pulse"
    )
}

@Composable
fun FeatureScreen(page: FeaturePage, onNext: () -> Unit, onSkip: () -> Unit) {
    val colors = AppTheme.colors
    val stepIndex = page.ordinal

    // The old fragment ran an ObjectAnimator per page: a scan-line sweep, a
    // pulsing halo, or nothing.
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (page.animation == "pulse") 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            "Skip",
            fontSize = 16.sp,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.End).clickable(onClick = onSkip).padding(8.dp)
        )

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            if (page.animation == "pulse") {
                Box(
                    Modifier.size(160.dp).scale(pulse).clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.16f))
                )
            }
            IconBadge(
                icon = page.icon,
                size = 145.dp,
                iconSize = 62.dp,
                corner = 38.dp
            )
        }

        Spacer(Modifier.height(60.dp))
        Text(
            page.title,
            fontSize = 27.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(36.dp))
        Text(
            page.subtitle,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        // Dots: the active one is an elongated pill, the others small circles.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val active = i == stepIndex
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (active) 26.dp else 9.dp, height = 9.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.accent else colors.progressBg)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${stepIndex + 1} of 3",
                fontSize = 15.sp,
                color = colors.textSecondary
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onNext,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White
                )
            ) {
                Text(page.buttonText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
