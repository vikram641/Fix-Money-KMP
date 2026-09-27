package com.fixmoney.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.ui.AppTheme
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_fixmoney_logo
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `fragment_onboarding_welcome.xml`, now using the real assets copied
 * from the old project: the logo drawable and the `coin.json` Lottie that the
 * old app played on this screen.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val colors = AppTheme.colors

    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(
            Res.readBytes("files/lottie/coin.json").decodeToString()
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.ic_fixmoney_logo),
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("FixMoney", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        }

        Spacer(Modifier.weight(1f))

        Image(
            painter = rememberLottiePainter(
                composition = composition,
                iterations = Compottie.IterateForever
            ),
            contentDescription = null,
            modifier = Modifier.size(220.dp)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Take control of your money",
            fontSize = 27.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Track expenses, scan receipts, and hit your budget goals — all in one place.",
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            color = colors.textSecondary
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
        ) {
            Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.white)
        }
        Spacer(Modifier.height(32.dp))
    }
}
