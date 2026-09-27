package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_check
import com.fixmoney.resources.ic_wallet_outline
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.PrimaryButton
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Port of `fragment_onboarding_all_set.xml` - the recap before entering the app. */
@Composable
fun AllSetScreen(state: OnboardingState, onStartTracking: () -> Unit) {
    val colors = AppTheme.colors
    val currency = CurrencyConstants.byCode(state.currencyCode)
    val displayName = state.name.trim().ifEmpty { "there" }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            modifier = Modifier.size(130.dp).clip(CircleShape).background(colors.green),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(58.dp)
            )
        }

        Spacer(Modifier.height(40.dp))
        Text(
            "You're all set, $displayName!",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Your budget is ready to track. Here's a quick recap.",
            fontSize = 16.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(30.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.bgCard)
                .padding(vertical = 6.dp)
        ) {
            RecapRow(
                icon = Res.drawable.ic_wallet_outline,
                label = "Monthly budget",
                value = "${currency.symbol}${formatIndian(state.monthlyBudget)}"
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = colors.cardStroke
            )
            RecapRow(
                symbolText = currency.symbol,
                label = "Currency",
                value = currency.code
            )
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Start Tracking", onStartTracking)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RecapRow(
    label: String,
    value: String,
    icon: DrawableResource? = null,
    symbolText: String? = null,
    size: Dp = 46.dp
) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    symbolText.orEmpty(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}
