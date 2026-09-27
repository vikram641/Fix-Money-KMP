package com.fixmoney.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Port of `fragment_onboarding_setup_name.xml` - step 1 of 3.
 *
 * The old XML had a layout bug where the subtext and the field below it were
 * both constrained to the headline, so they overlapped (see the old app's
 * CLAUDE.md). A Column can't reproduce that class of bug at all.
 */
@Composable
fun SetupNameScreen(onBack: () -> Unit, onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val canContinue = name.isNotBlank()

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("‹", fontSize = 22.sp) }

            Spacer(Modifier.width(16.dp))
            LinearProgressIndicator(
                progress = { 1f / 3f },
                modifier = Modifier.weight(1f).height(4.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text("1 / 3", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(36.dp))
        Text(
            text = "What should we\ncall you?",
            fontSize = 26.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "We'll use this to personalize your dashboard and greetings.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        Text("Your name", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("e.g. Alex") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onContinue(name.trim()) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(32.dp))
    }
}
