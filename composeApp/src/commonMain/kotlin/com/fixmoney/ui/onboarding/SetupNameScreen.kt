package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_arrow_right
import com.fixmoney.resources.nd_edit
import com.fixmoney.resources.nd_person
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.horizontalSwipe
import com.fixmoney.ui.designType

/**
 * Step 1 of 3, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design 7/fixmoney_setup_user_name_obsidian_dark`).
 */
@Composable
fun SetupNameScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = Design.colors
    val t = designType()

    Column(
        modifier = Modifier.fillMaxSize()
            // A swipe honours the same gate the Continue button does - without
            // that it would walk straight past an empty name.
            .horizontalSwipe(
                onSwipeLeft = { if (state.name.isNotBlank()) onContinue() },
                onSwipeRight = onBack
            )
            .safeDrawingPadding()
            // The field sits mid-screen and the button is pinned to the bottom,
            // so without this the keyboard covers the button on a short device.
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        SetupHeader(step = 1, total = 3, onBack = onBack)

        // The body scrolls so the keyboard can shrink it. Previously it was a
        // fixed column ending in a flexible spacer: with the keyboard up that
        // spacer went to zero and the pinned button landed on the helper line.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(40.dp))
        SetupIconTile(Res.drawable.nd_person)

        Spacer(Modifier.height(24.dp))
        Text(
            "What should we\ncall you?",
            style = t.displayHero,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We'll use this to personalize your dashboard and greetings.",
            style = t.bodyMd.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = colors.textMuted
        )

        Spacer(Modifier.height(32.dp))
        Text("YOUR NAME", style = t.labelCaps, color = colors.textMuted)
        Spacer(Modifier.height(12.dp))

        // BasicTextField, not OutlinedTextField: the mockup's field is a plain
        // capsule with no floating label and no focus outline to fight with.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(33.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(33.dp))
                .padding(horizontal = 24.dp)
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.name.isEmpty()) {
                    Text(
                        "e.g. Vikram Singh",
                        style = t.titleMd.copy(fontSize = 17.sp),
                        color = colors.textMuted
                    )
                }
                BasicTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        t.titleMd.copy(fontSize = 17.sp, color = colors.textPrimary)
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(12.dp))
            LineIcon(Res.drawable.nd_edit, colors.textMuted, 18.dp)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "You can change this later in your Profile settings.",
            style = t.bodySm,
            color = colors.textMuted
        )

        Spacer(Modifier.height(24.dp))
        }

        GlowButton(
            text = "Continue",
            onClick = { if (state.name.isNotBlank()) onContinue() },
            trailing = Res.drawable.nd_arrow_right,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.name.isNotBlank()
        )
        Spacer(Modifier.height(28.dp))
    }
}
