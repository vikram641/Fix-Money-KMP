package com.fixmoney.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.ui.AppTheme
import org.koin.compose.koinInject

/**
 * Port of `fragment_settings.xml`. Export Data and SIP Calculator are shown but
 * not wired - they were separate screens in the old app and have not been
 * migrated. Floating Voice Quick-Add needs an Android overlay service with no
 * iOS equivalent at all, so its toggle only records the preference.
 */
@Composable
fun SettingsScreen(onDarkModeChanged: (Boolean) -> Unit, onOpenSip: () -> Unit = {}) {
    val colors = AppTheme.colors
    val prefs: OnboardingPrefs = koinInject()
    val appPrefs: AppPrefs = koinInject()

    val name = remember { prefs.getUserName()?.takeIf { it.isNotBlank() } ?: "there" }
    val currency = remember { prefs.getCurrencyCode() }
    var dark by remember { mutableStateOf(appPrefs.darkMode() ?: true) }
    var alerts by remember { mutableStateOf(appPrefs.budgetAlerts()) }
    var voice by remember { mutableStateOf(appPrefs.voiceBubble()) }
    var showExport by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)

        Spacer(Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .border(1.dp, colors.cardStroke, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.first().uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 26.sp, color = colors.textSecondary)
        }

        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .border(1.dp, colors.cardStroke, RoundedCornerShape(20.dp))
                .padding(vertical = 6.dp)
        ) {
            ToggleRow("🌙", "Dark Theme", null, dark) {
                dark = it
                appPrefs.setDarkMode(it)
                onDarkModeChanged(it)
            }
            RowDivider()
            ToggleRow("🔔", "Budget Alerts", null, alerts) {
                alerts = it; appPrefs.setBudgetAlerts(it)
            }
            RowDivider()
            ToggleRow(
                "🎙", "Floating Voice Quick-Add",
                "A floating bubble anywhere on screen — tap and speak to add an expense",
                voice
            ) { voice = it; appPrefs.setVoiceBubble(it) }
            RowDivider()
            ValueRow("💱", "Currency", CurrencyConstants.byCode(currency).code)
            RowDivider()
            ChevronRow("📤", "Export Data") { showExport = true }
            RowDivider()
            ChevronRow("📈", "SIP Calculator", onOpenSip)
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showExport) {
        ExportDataDialog(onDismiss = { showExport = false })
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = AppTheme.colors.cardStroke
    )
}

@Composable
private fun SettingIcon(glyph: String) {
    Box(
        modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = 20.sp) }
}

@Composable
private fun ToggleRow(
    glyph: String,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SettingIcon(glyph)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = colors.textSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.progressEnd,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.progressBg
            )
        )
    }
}

@Composable
private fun ValueRow(glyph: String, title: String, value: String) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SettingIcon(glyph)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 17.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, color = colors.textSecondary)
    }
}

@Composable
private fun ChevronRow(glyph: String, title: String, onClick: () -> Unit = {}) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SettingIcon(glyph)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 17.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text("›", fontSize = 24.sp, color = colors.textSecondary)
    }
}
