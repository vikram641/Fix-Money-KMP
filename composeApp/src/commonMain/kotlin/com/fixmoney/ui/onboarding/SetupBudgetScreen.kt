package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_arrow_right
import com.fixmoney.resources.nd_card
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.ui.Design
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.horizontalSwipe
import com.fixmoney.ui.designType
import org.koin.compose.koinInject

private const val BUDGET_MAX = 100_000
private val QUICK_PICKS = listOf(10_000, 25_000, 50_000)

/**
 * Step 2 of 3, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design 7/fixmoney_setup_monthly_budget_obsidian_dark`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupBudgetScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = Design.colors
    val t = designType()
    val symbol = CurrencyConstants.symbolFor(state.currencyCode)

    val repository: CategoryRepository = koinInject()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    LaunchedEffect(Unit) { categories = repository.getCategories() }

    Column(
        modifier = Modifier.fillMaxSize()
            .horizontalSwipe(onSwipeLeft = onContinue, onSwipeRight = onBack)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        SetupHeader(step = 2, total = 3, onBack = onBack)

        // The body scrolls and the button stays pinned. The mockup fits on a
        // 20:9 phone; on a short one the category row and the button fall off
        // the bottom otherwise. On a tall device this looks identical.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(32.dp))
            SetupIconTile(Res.drawable.nd_card)

            Spacer(Modifier.height(24.dp))
            Text("Set your monthly\nbudget", style = t.displayHero, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "You can always change this later in Settings.",
                style = t.bodyMd.copy(fontSize = 15.sp),
                color = colors.textMuted
            )

            Spacer(Modifier.height(28.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.accent)) { append(symbol) }
                    withStyle(SpanStyle(color = colors.textPrimary)) {
                        append(" ${formatIndian(state.monthlyBudget)}")
                    }
                },
                style = t.displayHero.copy(fontSize = 40.sp, lineHeight = 48.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QUICK_PICKS.forEach { amount ->
                    val selected = state.monthlyBudget == amount
                    Box(
                        modifier = Modifier.weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) colors.accent.copy(alpha = 0.16f) else colors.surfaceCard
                            )
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) colors.accent else colors.stroke,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { state.monthlyBudget = amount },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$symbol${formatIndian(amount)}",
                            style = t.numericSm,
                            color = if (selected) colors.textPrimary else colors.textMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Both the thumb and the track are custom. Material3's defaults
            // draw a tall bar thumb, cut a gap either side of it and park a
            // stop dot at the far end - three things the mockup does not have,
            // and none of them reachable through `colors`.
            val sliderColors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.stroke
            )
            Slider(
                value = state.monthlyBudget.toFloat(),
                onValueChange = { state.monthlyBudget = it.toInt() },
                valueRange = 0f..BUDGET_MAX.toFloat(),
                colors = sliderColors,
                thumb = {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.30f))
                        )
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
                    }
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = sliderColors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        modifier = Modifier.height(6.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${symbol}0", style = t.bodySm, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text("-", style = t.bodySm, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                Text(
                    "$symbol${formatIndian(BUDGET_MAX)}",
                    style = t.bodySm,
                    color = colors.textMuted
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textPrimary)) { append("Track these categories ") }
                    withStyle(SpanStyle(color = colors.textMuted)) { append("(optional)") }
                },
                style = t.titleMd.copy(fontSize = 15.sp)
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    val selected = category.id in state.selectedCategoryIds
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) colors.accent.copy(alpha = 0.16f)
                                    else colors.surfaceCard
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) colors.accent else colors.stroke,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { state.toggleCategory(category.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            LineIcon(
                                categoryGlyph(category.icon),
                                if (selected) colors.accent else colors.textMuted,
                                26.dp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            category.name,
                            style = t.bodySm,
                            color = if (selected) colors.textPrimary else colors.textMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        GlowButton(
            text = "Continue",
            onClick = onContinue,
            trailing = Res.drawable.nd_arrow_right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
    }
}
