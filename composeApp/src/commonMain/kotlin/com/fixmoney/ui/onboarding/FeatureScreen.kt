package com.fixmoney.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_arrow_right
import com.fixmoney.resources.nd_scan_doc
import com.fixmoney.resources.nd_sparkle
import com.fixmoney.resources.nd_budget
import com.fixmoney.resources.nd_sparkle_duo
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.horizontalSwipe
import com.fixmoney.ui.designType
import org.jetbrains.compose.resources.DrawableResource

/**
 * The three onboarding feature slides, rebuilt to the 2026 mockups
 * (`stitch_fixmoney_fintech_app_design (7)` and `(8)`).
 *
 * **Two of the three pages describe features this app does not have**, so the
 * copy is not the mockups'. The layout is.
 *
 * The old copy came across from the pre-KMP app and promised recurring-bill and
 * auto-debit tracking with calendar sync, then push alerts before a bill is due.
 * There is no bill, due-date or recurrence model in `shared/` and no
 * notification code anywhere - `budgetAlerts()` is read by its own Settings
 * switch and by nothing else. These are the **first three screens a new user
 * ever sees**, so shipping them would be promising features at the worst
 * possible moment to be wrong.
 *
 * They are replaced by two things the app genuinely does, both on Android and
 * iOS: per-category budgets, and asking Fixoo about your own spending. Receipt
 * scanning, page one, was already true and is unchanged.
 *
 * Restore a page the moment its feature lands - the shape here takes any three.
 */
enum class FeaturePage(
    val icon: DrawableResource,
    val tag: String,
    val tagIcon: DrawableResource,
    val title: String,
    val subtitle: String,
    val buttonText: String
) {
    Scan(
        icon = Res.drawable.nd_scan_doc,
        tag = "Instant AI receipt reading",
        tagIcon = Res.drawable.nd_sparkle,
        title = "Scan receipts in seconds",
        subtitle = "Snap a photo and AI fills in the amount, date, merchant and category for you.",
        buttonText = "Next"
    ),
    Budgets(
        icon = Res.drawable.nd_budget,
        tag = "Per-category caps",
        tagIcon = Res.drawable.nd_budget,
        title = "Set a cap, watch it fill",
        subtitle = "Give each category a monthly limit and see how much of it is left at a glance.",
        buttonText = "Next"
    ),
    Fixoo(
        icon = Res.drawable.nd_sparkle_duo,
        tag = "Fixoo, your spending copilot",
        tagIcon = Res.drawable.nd_sparkle,
        title = "Ask where it all went",
        subtitle = "Fixoo reads your own expenses and answers in plain language - no spreadsheets.",
        buttonText = "Let's go"
    );

    companion object {
        /** Kept in one place so the dots, the step line and the flow agree. */
        val count: Int get() = entries.size
    }
}

@Composable
fun FeatureScreen(
    page: FeaturePage,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit = {}
) {
    val colors = Design.colors
    val t = designType()
    val step = page.ordinal

    Column(
        modifier = Modifier.fillMaxSize()
            // Swipe left for the next page, right for the previous one - three
            // slides with dots read as a pager, so they behave like one.
            .horizontalSwipe(onSwipeLeft = onNext, onSwipeRight = onBack)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Skip",
            style = t.titleMd,
            color = colors.textMuted,
            modifier = Modifier.align(Alignment.End).clickable(onClick = onSkip).padding(8.dp)
        )

        Spacer(Modifier.weight(1f))

        GlowTile(icon = page.icon, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(34.dp))
        TagPill(
            text = page.tag,
            icon = page.tagIcon,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(24.dp))
        Text(
            page.title,
            style = t.displayHero,
            textAlign = TextAlign.Center,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text(
            page.subtitle,
            style = t.bodyMd.copy(fontSize = 15.sp, lineHeight = 24.sp),
            textAlign = TextAlign.Center,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))
        StepDots(current = step, total = FeaturePage.count)

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("STEP", style = t.labelCaps, color = colors.textMuted)
                Spacer(Modifier.height(2.dp))
                Row {
                    Text("${step + 1}", style = t.titleMd, color = colors.textPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("of ${FeaturePage.count}", style = t.titleMd, color = colors.textMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.wrapContentWidth().width(200.dp)) {
                GlowButton(
                    text = page.buttonText,
                    onClick = onNext,
                    trailing = Res.drawable.nd_arrow_right,
                    height = 56.dp
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
