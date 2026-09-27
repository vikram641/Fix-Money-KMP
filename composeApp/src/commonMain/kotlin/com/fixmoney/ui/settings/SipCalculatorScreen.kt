package com.fixmoney.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.onboarding.formatIndian
import org.koin.compose.koinInject
import kotlin.math.pow

/** Port of the old app's SIP Calculator screen. */
@Composable
fun SipCalculatorScreen(onBack: () -> Unit) {
    val colors = AppTheme.colors
    val prefs: OnboardingPrefs = koinInject()
    val symbol = remember { CurrencyConstants.symbolFor(prefs.getCurrencyCode()) }

    var monthly by remember { mutableStateOf("5000") }
    var ratePercent by remember { mutableStateOf("12") }
    var years by remember { mutableStateOf("10") }

    val amount = monthly.toIntOrNull() ?: 0
    val rate = ratePercent.toDoubleOrNull() ?: 0.0
    val period = years.toIntOrNull() ?: 0

    // Standard SIP future value: P * (((1+i)^n - 1) / i) * (1+i), monthly compounding.
    val months = period * 12
    val monthlyRate = rate / 12 / 100
    val future = when {
        amount <= 0 || months <= 0 -> 0L
        monthlyRate == 0.0 -> (amount.toLong() * months)
        else -> {
            val growth = (1 + monthlyRate).pow(months)
            (amount * ((growth - 1) / monthlyRate) * (1 + monthlyRate)).toLong()
        }
    }
    val invested = amount.toLong() * months
    val returns = (future - invested).coerceAtLeast(0)
    val investedShare = if (future <= 0) 0f else invested.toFloat() / future

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colors.textPrimary,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp)
            )
            Text(
                "SIP Calculator",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(end = 30.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "See how a monthly SIP investment could grow over time.",
            fontSize = 16.sp,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(colors.bgCard).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ESTIMATED FUTURE VALUE", fontSize = 15.sp, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            Text(
                "$symbol${formatIndian(future.toInt())}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                Box(Modifier.fillMaxHeight().weight(investedShare.coerceAtLeast(0.01f)).background(colors.accent))
                Box(Modifier.fillMaxHeight().weight((1f - investedShare).coerceAtLeast(0.01f)).background(colors.green))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    LegendLabel("Invested", colors.accent)
                    Text(
                        "$symbol${formatIndian(invested.toInt())}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    LegendLabel("Est. Returns", colors.green)
                    Text(
                        "$symbol${formatIndian(returns.toInt())}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(colors.bgCard).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            NumberField("Monthly Investment", "e.g. 5000", monthly) { monthly = it }
            NumberField("Expected Return (% per year)", "e.g. 12", ratePercent) { ratePercent = it }
            NumberField("Time Period (Years)", "e.g. 10", years) { years = it }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun LegendLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 15.sp, color = color)
    }
}

@Composable
private fun NumberField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    val colors = AppTheme.colors
    Column {
        Text(label, fontSize = 15.sp, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.cardStroke, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            if (value.isEmpty()) Text(hint, fontSize = 18.sp, color = colors.textHint)
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(7)) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
