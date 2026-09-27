package com.fixmoney.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.*
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.model.DefaultCategories
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.IconBadge
import com.fixmoney.ui.components.PrimaryButton
import com.fixmoney.ui.components.StepHeader
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val BUDGET_MAX = 100_000

/** Port of `fragment_onboarding_setup_budget.xml` - step 2 of 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupBudgetScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = AppTheme.colors
    val symbol = CurrencyConstants.symbolFor(state.currencyCode)

    // The old ConstraintLayout had no ScrollView - it only ever fitted because
    // the reference device is 20:9. On a shorter screen (iPhone SE) the category
    // row and the Continue button fell off the bottom, so the body scrolls and
    // the button stays pinned. On a tall device this looks identical.
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)
    ) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        StepHeader(step = 2, total = 3, onBack = onBack)

        Spacer(Modifier.height(26.dp))
        IconBadge(Res.drawable.ic_wallet_outline, size = 70.dp, iconSize = 32.dp, corner = 21.dp)

        Spacer(Modifier.height(22.dp))
        Text(
            "Set your monthly\nbudget",
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "You can always change this later in Settings.",
            fontSize = 15.sp,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symbol, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            Spacer(Modifier.width(4.dp))
            BasicTextField(
                value = if (state.monthlyBudget == 0) "" else state.monthlyBudget.toString(),
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(7)
                    state.monthlyBudget = digits.toIntOrNull() ?: 0
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 60.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            listOf(10_000, 25_000, 50_000).forEach { preset ->
                val selected = state.monthlyBudget == preset
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(colors.bgCard)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) colors.accent else colors.cardStroke,
                            shape = CircleShape
                        )
                        .clickable { state.monthlyBudget = preset }
                        .padding(vertical = 14.dp)
                ) {
                    Text(
                        "$symbol${formatIndian(preset)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val sliderColors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.progressBg
        )
        Slider(
            value = state.monthlyBudget.coerceIn(0, BUDGET_MAX).toFloat(),
            onValueChange = { state.monthlyBudget = (it / 500).toInt() * 500 },
            valueRange = 0f..BUDGET_MAX.toFloat(),
            colors = sliderColors,
            // Material3 1.3+ draws the thumb as a tall vertical bar; the old
            // app's was a plain round handle.
            thumb = {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(colors.accent)
                )
            },
            // Its default track also draws a gap around the thumb and a stop
            // dot at the end; the old app's was a plain two-tone bar.
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    modifier = Modifier.height(6.dp)
                )
            }
        )
        Text(
            "${symbol}0 — $symbol${formatIndian(BUDGET_MAX)}",
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Track these categories (optional)",
            fontSize = 15.sp,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(DefaultCategories.ALL, key = { it.id }) { category ->
                val selected = category.id in state.selectedCategoryIds
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.bgCard)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) colors.accent else colors.cardStroke,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { state.toggleCategory(category.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(categoryIcon(category.icon)),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        category.name,
                        fontSize = 13.sp,
                        color = if (selected) colors.accent else colors.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
      }

        PrimaryButton("Continue", onContinue, enabled = state.monthlyBudget > 0)
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Maps a category's stored icon name onto its drawable. The old app resolved
 * this with `resources.getIdentifier(...)`, which has no Compose-resources
 * equivalent - accessors are generated, so the mapping has to be explicit.
 */
private fun categoryIcon(name: String): DrawableResource = when (name) {
    "ic_food" -> Res.drawable.ic_food
    "ic_transport" -> Res.drawable.ic_transport
    "ic_shopping" -> Res.drawable.ic_shopping
    "ic_health" -> Res.drawable.ic_health
    "ic_bills" -> Res.drawable.ic_bills
    "ic_entertainment" -> Res.drawable.ic_entertainment
    else -> Res.drawable.ic_other
}

/** 25000 -> "25,000", 100000 -> "1,00,000" - Indian digit grouping, as the old layout showed. */
internal fun formatIndian(value: Int): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val head = s.dropLast(3)
    val tail = s.takeLast(3)
    val grouped = head.reversed().chunked(2).joinToString(",").reversed()
    return "$grouped,$tail"
}
