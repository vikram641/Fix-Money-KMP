package com.fixmoney.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_fixmoney_logo
import com.fixmoney.resources.ic_wallet_outline
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.IconBadge
import com.fixmoney.ui.components.PrimaryButton
import org.jetbrains.compose.resources.painterResource

/** Port of `fragment_onboarding_welcome.xml`. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_fixmoney_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text("FixMoney", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        }

        Spacer(Modifier.weight(1f))

        IconBadge(
            icon = Res.drawable.ic_wallet_outline,
            size = 160.dp,
            iconSize = 72.dp,
            corner = 40.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(52.dp))
        Text(
            text = "Take control of your money",
            fontSize = 30.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(34.dp))
        Text(
            text = "Track expenses, scan receipts, and hit your budget goals — all in one place.",
            fontSize = 16.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton("Get Started", onGetStarted)
        Spacer(Modifier.height(40.dp))
    }
}
