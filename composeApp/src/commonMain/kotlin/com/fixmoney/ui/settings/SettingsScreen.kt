package com.fixmoney.ui.settings

import androidx.compose.runtime.collectAsState
import com.fixmoney.ui.coins.LocalCoins
import com.fixmoney.ui.coins.trialDaysLeft
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.*
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.onboarding.PreviewToggle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.fixmoney.ui.ArmThemeReveal
import com.fixmoney.ui.Design
import com.fixmoney.ui.LocalThemeReveal
import com.fixmoney.ui.DesignColors
import com.fixmoney.ui.components.CollapsingScreen
import com.fixmoney.ui.components.ScreenTitleBand
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.isEnabled
import com.fixmoney.ui.designType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

/**
 * Settings, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design/fixmoney_settings/`).
 *
 * **Most of this mockup describes features that do not exist**, and the user
 * asked for the full layout anyway, with the switches live. So they are:
 * every switch flips and the choice is persisted through
 * `AppPrefs.preview(...)`, and **nothing reads those preferences back yet**.
 *
 * Rows in that state carry a small **PREVIEW** pill. Without it, "Smart Cap
 * Rollover - sweep unspent budget directly to emergency reserve" reads as a
 * promise that money is being moved, which is the one kind of misunderstanding
 * this app must not create. Drop the pill from a row when its feature ships.
 *
 * Four rows are real and unmarked: Voice & NLP Ingestion (the Android floating
 * bubble), Base Currency, Obsidian Pulse (the dark theme) and Export Financial
 * Ledger. SIP Calculator is kept from the old screen.
 *
 * **Connected Accounts shows an empty state.** There is no bank integration, so
 * the section offers the link row - marked unavailable - and lists no accounts.
 * The mockup's HDFC and ICICI balances are invented, and rendering invented
 * figures as the user's money is not something to ship at any fidelity.
 */
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onOpenSip: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val colors = Design.colors
    val t = designType()
    val prefs: OnboardingPrefs = koinInject()
    val appPrefs: AppPrefs = koinInject()
    val voice = rememberVoiceQuickAdd()
    val coins = LocalCoins.current
    val coinWallet = coins?.repo?.wallet?.collectAsState()?.value
    val themeReveal = LocalThemeReveal.current
    val scope = rememberCoroutineScope()

    // Frame recording for the theme wipe costs something, so it runs only while
    // this screen - the only place the theme can be switched - is on screen.
    ArmThemeReveal()

    val name = prefs.getUserName()?.takeIf { it.isNotBlank() } ?: "there"
    val currency = prefs.getCurrencyCode()

    var alerts by remember { mutableStateOf(appPrefs.budgetAlerts()) }
    var showExport by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    LaunchedEffect(voice.message) {
        if (voice.message != null) { delay(5000); voice.clearMessage() }
    }

    CollapsingScreen(
        title = null,
        scroll = scroll,
        sticky = {
            Column {
                ScreenTitleBand {
                    Text(
                        "Settings",
                        style = t.headlineLg.copy(fontSize = 22.sp),
                        color = colors.textPrimary
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        },
        body = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ProfileCard(name = name, onEdit = onOpenProfile)

                if (isEnabled(FeatureFlag.SectionAi)) {
                    Section(
                        "AI & INTELLIGENCE",
                        trailing = "Fixoo v1",
                        rows = buildList {
                            // AI coins: the way into the wallet from Settings.
                            if (coins != null && isEnabled(FeatureFlag.CoinWallet)) {
                                add(SettingsRow("coins") {
                                    ChevronRow(
                                        icon = Res.drawable.nd_wallet,
                                        title = "Fixoo Coins",
                                        subtitle = when {
                                            coinWallet == null -> "Watch videos to earn Fixoo Coins for AI features"
                                            coinWallet.premiumActive -> "Fixoo Premium active · AI without coins"
                                            coinWallet.trialActive -> "Free trial · ${trialDaysLeft(coinWallet)} days left"
                                            else -> "${coinWallet.coins} coins · ${coinWallet.adsLeftToday} videos left today"
                                        },
                                        tinted = true,
                                        onClick = coins.openWallet
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.AutonomousGuard)) {
                                add(SettingsRow("guard") {
                                    PreviewToggleRow(
                                        icon = Res.drawable.nd_shield,
                                        title = "Fixoo Autonomous Guard",
                                        subtitle = "Real-time alerts when burn pace threatens month-end runway",
                                        toggle = PreviewToggle.AutonomousGuard,
                                        appPrefs = appPrefs
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.InsightFrequency)) {
                                add(SettingsRow("insight-frequency") {
                                    ValueRow(
                                        icon = Res.drawable.nd_bell,
                                        title = "Insight Frequency",
                                        subtitle = "Push briefs & burn audits",
                                        value = appPrefs.insightFrequency(),
                                        preview = true
                                    )
                                })
                            }
                            // Real: the Android floating bubble. Hidden on iOS, where no
                            // app can draw over another.
                            if (voice.supported && isEnabled(FeatureFlag.VoiceQuickAdd)) {
                                add(SettingsRow("voice") {
                                    ToggleRow(
                                        icon = Res.drawable.nd_mic,
                                        title = "Voice & NLP Ingestion",
                                        subtitle = "Parse conversational spends via a floating bubble",
                                        checked = voice.enabled,
                                        message = voice.message,
                                        onChange = { voice.toggle(it) }
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.NeuralCategorization)) {
                                add(SettingsRow("neural") {
                                    PreviewToggleRow(
                                        icon = Res.drawable.nd_neural,
                                        title = "Neural Categorization",
                                        subtitle = "Auto-tag merchants as they are added",
                                        toggle = PreviewToggle.NeuralCategorization,
                                        appPrefs = appPrefs
                                    )
                                })
                            }
                        }
                    )
                }

                if (isEnabled(FeatureFlag.SectionConnectedAccounts)) {
                    Section(
                        "CONNECTED ACCOUNTS",
                        rows = listOf(SettingsRow("accounts") { EmptyAccounts() })
                    )
                }

                if (isEnabled(FeatureFlag.SectionPreferences)) {
                    Section(
                        "PREFERENCES & CURRENCY",
                        rows = buildList {
                            if (isEnabled(FeatureFlag.BaseCurrency)) {
                                add(SettingsRow("currency") {
                                    ValueRow(
                                        icon = Res.drawable.nd_rupee,
                                        title = "Base Currency",
                                        subtitle = "Used for every amount in the app",
                                        value = "${CurrencyConstants.symbolFor(currency)} $currency"
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.FiscalCycleStart)) {
                                add(SettingsRow("fiscal-cycle") {
                                    ValueRow(
                                        icon = Res.drawable.nd_cycle,
                                        title = "Fiscal Cycle Start",
                                        subtitle = "Budget allocation reset day",
                                        value = ordinal(appPrefs.fiscalCycleStart()) + " of month",
                                        preview = true
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.BudgetAlerts)) {
                                add(SettingsRow("budget-alerts") {
                                    ToggleRow(
                                        icon = Res.drawable.nd_bell,
                                        title = "Budget Alerts",
                                        subtitle = "Warn as a category nears its cap",
                                        checked = alerts,
                                        onChange = { alerts = it; appPrefs.setBudgetAlerts(it) }
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.SmartCapRollover)) {
                                add(SettingsRow("rollover") {
                                    PreviewToggleRow(
                                        icon = Res.drawable.nd_savings,
                                        title = "Smart Cap Rollover",
                                        subtitle = "Sweep unspent budget into next month's envelope",
                                        toggle = PreviewToggle.SmartCapRollover,
                                        appPrefs = appPrefs
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.BiometricShield)) {
                                add(SettingsRow("biometric") {
                                    PreviewToggleRow(
                                        icon = Res.drawable.nd_fingerprint,
                                        title = "Biometric Shield",
                                        subtitle = "Face or fingerprint on unlock",
                                        toggle = PreviewToggle.BiometricShield,
                                        appPrefs = appPrefs
                                    )
                                })
                            }
                        }
                    )
                }

                if (isEnabled(FeatureFlag.SectionAppearance)) {
                    Section(
                        "APPEARANCE & HAPTICS",
                        rows = buildList {
                            if (isEnabled(FeatureFlag.DarkThemeToggle)) {
                                add(SettingsRow("theme") {
                                    // The new theme is wiped in from this row
                                    // rather than snapping, so the row has to
                                    // report where on screen it sits.
                                    var switchAt by remember { mutableStateOf(Offset.Zero) }
                                    Box(
                                        Modifier.onGloballyPositioned {
                                            switchAt = it.boundsInRoot().centerRight
                                        }
                                    ) {
                                        ToggleRow(
                                            icon = Res.drawable.nd_moon,
                                            title = "Obsidian Pulse",
                                            subtitle = "Deep slate dark theme",
                                            checked = darkTheme,
                                            onChange = { want ->
                                                val apply = {
                                                    appPrefs.setDarkMode(want)
                                                    onDarkModeChanged(want)
                                                }
                                                if (themeReveal == null) apply()
                                                else scope.launch { themeReveal.reveal(switchAt, apply) }
                                            }
                                        )
                                    }
                                })
                            }
                            if (isEnabled(FeatureFlag.FrostedGlass)) {
                                add(SettingsRow("frosted-glass") {
                                    PreviewToggleRow(
                                        icon = Res.drawable.nd_glass,
                                        title = "Frosted Glass & Ambient Glows",
                                        subtitle = "Blur and glow behind cards",
                                        toggle = PreviewToggle.FrostedGlass,
                                        appPrefs = appPrefs
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.TactileHaptics)) {
                                add(SettingsRow("haptics") {
                                    PreviewToggleRow(
                                        icon = Res.drawable.nd_vibration,
                                        title = "Tactile Haptics",
                                        subtitle = "Micro-pulses on slider ticks & transactions",
                                        toggle = PreviewToggle.TactileHaptics,
                                        appPrefs = appPrefs
                                    )
                                })
                            }
                        }
                    )
                }

                if (isEnabled(FeatureFlag.SectionTools)) {
                    Section(
                        "TOOLS & EXPORTS",
                        rows = buildList {
                            if (isEnabled(FeatureFlag.ExportData)) {
                                add(SettingsRow("export") {
                                    ChevronRow(
                                        icon = Res.drawable.nd_download,
                                        title = "Export Financial Ledger",
                                        subtitle = "Download this month's spending as CSV",
                                        tinted = true,
                                        onClick = { showExport = true }
                                    )
                                })
                            }
                            if (isEnabled(FeatureFlag.SipCalculator)) {
                                add(SettingsRow("sip") {
                                    ChevronRow(
                                        icon = Res.drawable.nd_analytics,
                                        title = "SIP Calculator",
                                        subtitle = "Project a monthly investment",
                                        onClick = onOpenSip
                                    )
                                })
                            }
                            // "Zero-Knowledge Keyphrase / Offline seed storage for
                            // the local vault" was removed rather than left behind
                            // its flag. Nothing here is zero-knowledge and there is
                            // no vault - expenses sit in a plain Room database. A
                            // security claim in a finance app is exactly the kind
                            // Play's misrepresentation policy is about, and a
                            // remote flag is not a safe place to hold one: flags
                            // fail *open*, so a device that cannot reach Firestore
                            // would show the claim anyway.
                        }
                    )
                }

                AppFooter()
                Spacer(Modifier.height(72.dp))
            }
        }
    )

    if (showExport) {
        ExportDataDialog(onDismiss = { showExport = false })
    }
}

/* ---------------------------------------------------------- profile card */

@Composable
private fun ProfileCard(name: String, onEdit: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ProfileAvatar(name, 54.dp, shapeRadius = 16.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = t.headlineSm,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // The mockup shows an email address. There is no account and no
                // email anywhere in this build, so the line says what is true.
                Text("Local profile on this device", style = t.bodySm, color = colors.textMuted)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceHigh)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_edit, colors.textMuted, 17.dp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(colors.surface).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(Res.drawable.nd_cloud_check, colors.positive, 15.dp)
            Spacer(Modifier.width(7.dp))
            Text("Data stored on this device", style = t.bodySm, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            // "Synchronized" in the mockup. Nothing syncs - there is no backend.
            Text("Offline", style = t.numericSm, color = colors.positive)
        }
    }
}

/* ------------------------------------------------------- connected accounts */

@Composable
private fun EmptyAccounts() {
    val colors = Design.colors
    val t = designType()
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_bank, colors.textMuted, 19.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("No accounts linked", style = t.titleMd, color = colors.textPrimary)
                Text(
                    "Expenses are entered by hand, scanned or dictated",
                    style = t.bodySm,
                    color = colors.textMuted
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(colors.surface).padding(vertical = 13.dp)
        ) {
            LineIcon(Res.drawable.nd_plus_circle, colors.textMuted, 17.dp)
            Spacer(Modifier.width(8.dp))
            Text("Link bank / card", style = t.numericSm, color = colors.textMuted)
            Spacer(Modifier.width(8.dp))
            PreviewPill()
        }
    }
}

/* ------------------------------------------------------------------- rows */

@Composable
private fun Section(
    title: String,
    rows: List<SettingsRow>,
    trailing: String? = null
) {
    // Nothing survived the flags - drop the header and the card too, rather
    // than leaving a titled empty box.
    if (rows.isEmpty()) return

    val colors = Design.colors
    val t = designType()
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Text(title, style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            trailing?.let { Text(it, style = t.labelCaps, color = colors.accent) }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, RoundedCornerShape(20.dp))
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                key(row.id) { row.content() }
            }
        }
    }
}

/**
 * One row of a [Section], paired with a stable [id].
 *
 * Rows reach [Section] as a list rather than a `content` lambda because nearly
 * every one of them is now behind a flag. Building the list first is what lets
 * the section **place the dividers itself** - between surviving rows only - and
 * skip itself entirely when none survive. With the dividers left to the callers
 * they had to guess, and a hidden row stranded a hairline above or below it.
 *
 * [id] keys the row in composition, so hiding one row cannot hand its
 * remembered state to whichever row slides up into its place.
 */
private class SettingsRow(val id: String, val content: @Composable () -> Unit)

@Composable
private fun RowDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .height(HAIRLINE).background(Design.colors.stroke)
    )
}

@Composable
private fun RowIcon(icon: DrawableResource, tinted: Boolean = false) {
    val colors = Design.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (tinted) colors.accent.copy(alpha = 0.15f) else colors.surface),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(icon, if (tinted) colors.accent else colors.textMuted, 19.dp)
    }
}

/** Marks a row whose feature is not built yet. See this file's doc comment. */
@Composable
private fun PreviewPill() {
    val colors = Design.colors
    val t = designType()
    Box(
        Modifier.clip(CircleShape)
            .background(colors.textMuted.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text("PREVIEW", style = t.labelCaps, color = colors.textMuted)
    }
}

@Composable
private fun RowShell(
    icon: DrawableResource,
    title: String,
    subtitle: String?,
    preview: Boolean = false,
    tinted: Boolean = false,
    titleTint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        RowIcon(icon, tinted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // The pill sits on the **subtitle** line, not the title's. Sharing
            // the title's line cost ~90dp and truncated every long name
            // ("Fixoo Autonomous Gu…"); the title is the row's identity and has
            // to survive, while the subtitle can ellipsise.
            Text(
                title,
                style = t.titleMd,
                color = titleTint ?: colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null || preview) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview) {
                        PreviewPill()
                        Spacer(Modifier.width(7.dp))
                    }
                    subtitle?.let {
                        Text(
                            it,
                            style = t.bodySm,
                            color = colors.textMuted,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun ToggleRow(
    icon: DrawableResource,
    title: String,
    subtitle: String?,
    checked: Boolean,
    message: String? = null,
    preview: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Column {
        RowShell(icon, title, subtitle, preview = preview) {
            DesignSwitch(checked, onChange)
        }
        message?.let {
            Text(
                it,
                style = t.bodySm,
                color = colors.negative,
                modifier = Modifier.padding(start = 66.dp, end = 14.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun PreviewToggleRow(
    icon: DrawableResource,
    title: String,
    subtitle: String,
    toggle: PreviewToggle,
    appPrefs: AppPrefs
) {
    var checked by remember(toggle) { mutableStateOf(appPrefs.preview(toggle)) }
    ToggleRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        preview = true,
        onChange = { checked = it; appPrefs.setPreview(toggle, it) }
    )
}

@Composable
private fun ValueRow(
    icon: DrawableResource,
    title: String,
    subtitle: String?,
    value: String,
    preview: Boolean = false
) {
    val colors = Design.colors
    val t = designType()
    RowShell(icon, title, subtitle, preview = preview) {
        Text(value, style = t.numericSm, color = colors.textPrimary, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        LineIcon(Res.drawable.nd_chevron_right, colors.textMuted, 15.dp)
    }
}

@Composable
private fun ChevronRow(
    icon: DrawableResource,
    title: String,
    subtitle: String?,
    tinted: Boolean = false,
    preview: Boolean = false,
    onClick: () -> Unit
) {
    val colors = Design.colors
    RowShell(icon, title, subtitle, preview = preview, tinted = tinted, onClick = onClick) {
        LineIcon(Res.drawable.nd_chevron_right, colors.textMuted, 15.dp)
    }
}

/** The mockup's gradient pill switch. */
@Composable
private fun DesignSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Design.colors
    Box(
        Modifier.size(width = 46.dp, height = 27.dp).clip(CircleShape)
            .background(
                if (checked) Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                else Brush.linearGradient(listOf(colors.surfaceHigh, colors.surfaceHigh))
            )
            .clickable { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(21.dp).clip(CircleShape).background(Color.White))
    }
}

/* ----------------------------------------------------------------- footer */

@Composable
private fun AppFooter() {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Res.drawable.nd_sparkle, colors.accent, 15.dp)
            Spacer(Modifier.width(6.dp))
            Text("FixMoney v${appVersionName()}", style = t.numericSm, color = colors.textMuted)
        }
    }
}

private fun ordinal(day: Int): String {
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}
