package com.fixmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.shared.onboarding.OnboardingPrefs
import org.koin.compose.koinInject

/**
 * Stand-in for the real dashboard. It reads the name back out of
 * OnboardingPrefs, so seeing it here proves the whole chain worked:
 * Compose input -> shared Koin-injected prefs -> platform storage
 * (SharedPreferences / NSUserDefaults) -> read back after navigation.
 */
@Composable
fun HomePlaceholderScreen() {
    val prefs: OnboardingPrefs = koinInject()
    val name = prefs.getUserName()

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (name.isNullOrBlank()) "Welcome back" else "Hi, $name",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Dashboard coming next",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Running on ${platformName()}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
