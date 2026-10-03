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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.*
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.formatIndian
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject
import kotlin.math.pow

/**
 * SIP Calculator, rebuilt to the 2026 mockup
 * (`fixmoney_sip_calculator_obsidian_theme`).
 *
 * The arithmetic is the standard SIP future value - `P * (((1+i)^n - 1) / i) *
 * (1+i)` with monthly compounding - and every figure on screen falls out of it,
 * so the whole screen is real.
 *
 * **"Start SIP Plan" is not here.** The mockup's primary button implies placing
 * an actual investment; there is no broker, no account and no money movement in
 * this app, and a button that looks like it starts an investment and does
 * nothing is the worst kind to ship. The screen is a calculator and says so.
 *
 * Also dropped: "Sovereign Wealth Modeler", "WEALTH MATRIX V2.4", "FixMoney
 * Enclave Computation" and the build string - invented product branding
 * standing in for nothing. "Compounded monthly" stays, because that is what the
 * formula actually does and it changes how the number should be read.
 */
@Composable
fun SipCalculatorScreen(onBack: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val prefs: OnboardingPrefs = koinInject()
    val symbol = remember { CurrencyConstants.symbolFor(prefs.getCurrencyCode()) }

    var monthly by remember { mutableFloatStateOf(5_000f) }
    var ratePercent by remember { mutableFloatStateOf(12f) }
    var years by remember { mutableFloatStateOf(10f) }

    val amount = monthly.toInt()
    val period = years.toInt()
    val months = period * 12
    val monthlyRate = ratePercent / 12.0 / 100.0

    val future = when {
        amount <= 0 || months <= 0 -> 0L
        monthlyRate == 0.0 -> amount.toLong() * months
        else -> {
            val growth = (1 + monthlyRate).pow(months)
            (amount * ((growth - 1) / monthlyRate) * (1 + monthlyRate)).toLong()
        }
    }
    val invested = amount.toLong() * months
    val returns = (future - invested).coerceAtLeast(0)
    val gainPercent = if (invested <= 0) 0 else ((returns * 100) / invested).toInt()
    val investedShare = if (future <= 0) 0f else (invested.toFloat() / future)

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceCard)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_arrow_left, colors.textPrimary, 19.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("SIP Calculator", style = t.headlineSm, color = colors.textPrimary)
                Text("Compounded monthly", style = t.bodySm, color = colors.textMuted)
            }
            Box(
                Modifier.clip(CircleShape).background(colors.positive.copy(alpha = 0.15f))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text("${ratePercent.toInt()}% p.a.", style = t.numericSm, color = colors.positive)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Text("WEALTH PROJECTION", style = t.labelCaps, color = colors.textMuted)
                Spacer(Modifier.height(12.dp))
                Text("Estimated future value", style = t.bodySm, color = colors.textMuted)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$symbol${formatIndian(future.toInt())}",
                        style = t.displayHero.copy(fontSize = 30.sp),
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 5.dp)
                    ) {
                        LineIcon(Res.drawable.nd_trend_up, colors.positive, 13.dp)
                        Spacer(Modifier.width(3.dp))
                        Text("+$gainPercent%", style = t.numericSm, color = colors.positive)
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Invested vs returns as one bar - the split is the whole point
                // of the screen, and two numbers below it are easy to skim past.
                Row(
                    Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                        .background(colors.surfaceHigh)
                ) {
                    if (investedShare > 0f) {
                        Box(
                            Modifier.weight(investedShare.coerceIn(0.02f, 1f)).fillMaxHeight()
                                .clip(CircleShape).background(colors.accentStrong)
                        )
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier.weight((1f - investedShare).coerceAtLeast(0.001f))
                                .fillMaxHeight().clip(CircleShape).background(colors.positive)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SplitTile(
                        "INVESTED",
                        "$symbol${formatIndian(invested.toInt())}",
                        colors.accentStrong,
                        Modifier.weight(1f)
                    )
                    SplitTile(
                        "EST. RETURNS",
                        "$symbol${formatIndian(returns.toInt())}",
                        colors.positive,
                        Modifier.weight(1f)
                    )
                }
            }

            Text("INVESTMENT PARAMETERS", style = t.labelCaps, color = colors.textMuted)

            ParameterCard(
                icon = Res.drawable.nd_rupee,
                title = "Monthly investment",
                caption = "Monthly recurring",
                value = monthly,
                valueText = formatIndian(amount),
                prefix = symbol,
                range = 500f..100_000f,
                step = 500f,
                minLabel = "$symbol${formatIndian(500)}",
                midLabel = "$symbol${formatIndian(50_000)}",
                maxLabel = "$symbol${formatIndian(100_000)}",
                onValue = { monthly = it },
                onText = { monthly = (it.toFloatOrNull() ?: 0f).coerceIn(0f, 100_000f) }
            )

            ParameterCard(
                icon = Res.drawable.nd_analytics,
                title = "Expected return",
                caption = "% per year",
                value = ratePercent,
                valueText = ratePercent.toInt().toString(),
                suffix = "%",
                range = 1f..30f,
                step = 1f,
                minLabel = "1%",
                midLabel = "15%",
                maxLabel = "30%",
                onValue = { ratePercent = it },
                onText = { ratePercent = (it.toFloatOrNull() ?: 1f).coerceIn(1f, 30f) }
            )

            ParameterCard(
                icon = Res.drawable.nd_calendar,
                title = "Time horizon",
                caption = "Tenure span",
                value = years,
                valueText = period.toString(),
                suffix = if (period == 1) "YEAR" else "YEARS",
                range = 1f..35f,
                step = 1f,
                minLabel = "1 yr",
                midLabel = "15 yrs",
                maxLabel = "35 yrs",
                onValue = { years = it },
                onText = { years = (it.toFloatOrNull() ?: 1f).coerceIn(1f, 35f) }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                LineIcon(Res.drawable.nd_alert_circle, colors.textMuted, 15.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    "An estimate at a constant rate. Real returns vary and are not guaranteed.",
                    style = t.bodySm,
                    color = colors.textMuted
                )
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SplitTile(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = Design.colors
    val t = designType()
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(6.dp))
            Text(label, style = t.labelCaps, color = colors.textMuted)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = t.numericMd, color = tint, maxLines = 1)
    }
}

/**
 * One parameter: a typed field and a slider over the same value.
 *
 * The field is the reason [onText] exists separately from [onValue] - typing
 * "7" into a 1..35 slider must not clamp to the nearest step mid-keystroke, so
 * the text path coerces only to the range's bounds.
 */
// `SliderDefaults.Track(sliderState = ...)` is still experimental in Material3
// 1.8; it is the only way to draw the mockup's unbroken track, and
// SetupBudgetScreen already opts in for the same reason.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterCard(
    icon: DrawableResource,
    title: String,
    caption: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    minLabel: String,
    midLabel: String,
    maxLabel: String,
    onValue: (Float) -> Unit,
    onText: (String) -> Unit,
    prefix: String? = null,
    suffix: String? = null
) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(icon, colors.accent, 16.dp)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = t.titleMd, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(caption, style = t.bodySm, color = colors.textMuted)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .padding(horizontal = 14.dp)
        ) {
            prefix?.let {
                Text(it, style = t.titleMd, color = colors.textMuted)
                Spacer(Modifier.width(6.dp))
            }
            BasicTextField(
                value = valueText,
                onValueChange = { onText(it.filter { c -> c.isDigit() }.take(7)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = t.headlineSm.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f)
            )
            suffix?.let { Text(it, style = t.numericSm, color = colors.textMuted) }
        }

        Spacer(Modifier.height(10.dp))
        val sliderColors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accentStrong,
            inactiveTrackColor = colors.surfaceHigh
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onValue((it / step).toInt() * step) },
            valueRange = range,
            colors = sliderColors,
            // Material3 1.3+ draws a tall bar thumb and a gap around it; the
            // mockup's is a plain glowing handle on an unbroken track.
            thumb = {
                Box(Modifier.size(20.dp).clip(CircleShape).background(colors.accent))
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    modifier = Modifier.height(5.dp)
                )
            }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(minLabel, style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            Text(midLabel, style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            Text(maxLabel, style = t.labelCaps, color = colors.textMuted)
        }
    }
}
