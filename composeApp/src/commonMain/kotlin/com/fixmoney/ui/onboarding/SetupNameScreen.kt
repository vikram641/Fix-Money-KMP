package com.fixmoney.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import com.fixmoney.resources.Res
import com.fixmoney.resources.ic_person_outline
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.components.IconBadge
import com.fixmoney.ui.components.PrimaryButton
import com.fixmoney.ui.components.StepHeader

/** Port of `fragment_onboarding_setup_name.xml` - step 1 of 3. */
@Composable
fun SetupNameScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        StepHeader(step = 1, total = 3, onBack = onBack)

        Spacer(Modifier.height(48.dp))
        IconBadge(Res.drawable.ic_person_outline, size = 74.dp, iconSize = 34.dp, corner = 22.dp)

        Spacer(Modifier.height(26.dp))
        Text(
            "What should we\ncall you?",
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We'll use this to personalize your dashboard and greetings.",
            fontSize = 15.sp,
            lineHeight = 23.sp,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(34.dp))
        Text("Your name", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))

        // BasicTextField rather than OutlinedTextField: the old field was a flat
        // filled box (`bg_field_input.xml`) with no outline and no floating label.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .background(colors.inputBg, RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (state.name.isEmpty()) {
                Text("e.g. Alex", fontSize = 18.sp, color = colors.textHint)
            }
            BasicTextField(
                value = state.name,
                onValueChange = { state.name = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", onContinue, enabled = state.name.isNotBlank())
        Spacer(Modifier.height(40.dp))
    }
}
