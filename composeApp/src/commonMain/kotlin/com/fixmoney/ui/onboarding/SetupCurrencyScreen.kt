package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_check
import com.fixmoney.resources.ic_currency_outline
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.IconBadge
import com.fixmoney.ui.components.PrimaryButton
import com.fixmoney.ui.components.StepHeader
import org.jetbrains.compose.resources.painterResource

/**
 * Port of `fragment_onboarding_setup_currency.xml` + `CurrencyAdapter` -
 * step 3 of 3. Lists all 14 currencies, as the old screen did.
 */
@Composable
fun SetupCurrencyScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        StepHeader(step = 3, total = 3, onBack = onBack)

        Spacer(Modifier.height(40.dp))
        IconBadge(Res.drawable.ic_currency_outline, size = 70.dp, iconSize = 32.dp, corner = 21.dp)

        Spacer(Modifier.height(22.dp))
        Text(
            "Choose your\ncurrency",
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "This is how amounts show up across the app.",
            fontSize = 15.sp,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(18.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(CurrencyConstants.CURRENCIES, key = { it.code }) { currency ->
                val selected = currency.code == state.currencyCode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.bgCard)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) colors.accent else colors.cardStroke,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { state.currencyCode = currency.code }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            currency.symbol,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            currency.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(currency.code, fontSize = 14.sp, color = colors.textSecondary)
                    }
                    if (selected) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton("Finish", onFinish)
        Spacer(Modifier.height(40.dp))
    }
}
