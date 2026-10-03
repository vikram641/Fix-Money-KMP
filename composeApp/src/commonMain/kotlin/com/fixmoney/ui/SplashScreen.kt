package com.fixmoney.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_fixmoney_logo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/**
 * The old app's splash, rebuilt: a small circular FixMoney mark centred on the
 * screen, then straight into the app.
 *
 * The logo sits on a **white** circle in both themes, not on `accent` - the
 * artwork is itself two shades of purple, so a purple plate swallowed it. Same
 * reason the Welcome header's badge is white.
 *
 * [onDone] fires once, from a `LaunchedEffect(Unit)`, so a recomposition (the
 * theme flipping, a configuration change) cannot navigate twice.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    // `bgSplash` is the *old* generated token (#FFFFFF light / #070E31 dark) and
    // matched neither surface once the app moved to the 2026 palette - the
    // splash flashed a different colour than the screen behind it. It uses the
    // redesign's own surface now, so the handoff is seamless.
    val design = Design.colors
    val appear = remember { Animatable(0.82f) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        delay(420)
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(design.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(appear.value)
                    .clip(CircleShape)
                    .background(Color.White)
                    // The surface is near-white in light mode, so without a ring
                    // the plate vanishes into the background.
                    .border(1.dp, design.stroke, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_fixmoney_logo),
                    contentDescription = "FixMoney",
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "FixMoney",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = design.textPrimary,
                modifier = Modifier.alpha(appear.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Track. Budget. Save.",
                fontSize = 14.sp,
                color = design.textMuted,
                modifier = Modifier.alpha(appear.value)
            )
        }
    }
}
