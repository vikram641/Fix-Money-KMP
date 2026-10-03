package com.fixmoney.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.*
import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.isoToEpochDay
import com.fixmoney.ui.Design
import com.fixmoney.ui.budget.prettyMonthLabel
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.formatIndian
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Edit Profile, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design (5)`).
 *
 * The profile is **local**: name and currency are the same `OnboardingPrefs`
 * keys onboarding wrote, so saving here updates Home's greeting and every
 * currency symbol in the app immediately. "Your Activity" is counted from the
 * expense rows.
 *
 * **Dropped from the mockup**, all of them data this app does not have:
 *
 * - **Email address** with a "Verified" tick and a locked field. There is no
 *   account and no email anywhere in this build.
 * - **"ID: FX-84920"**. An invented identifier - the same kind the user had
 *   removed from Expense Detail.
 * - **"256-Bit Hardware Enclave"** and the verified tick beside the name.
 * - **"Active Sync"** and **"Cloud ledger synchronized"**. Nothing syncs.
 * - The **camera badge** on the avatar. The old app's `AvatarManager` /
 *   `AvatarPickerBottomSheet` are not migrated, so there is nowhere to put a
 *   photo and no picker to choose one. The avatar is the initial.
 * - The header **overflow menu**, which has nothing behind it.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val prefs: OnboardingPrefs = koinInject()
    val dao: ExpenseDao = koinInject()

    var name by remember { mutableStateOf(prefs.getUserName().orEmpty()) }
    var currency by remember { mutableStateOf(prefs.getCurrencyCode()) }
    var currencyOpen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var expenseCount by remember { mutableIntStateOf(0) }
    var totalTracked by remember { mutableIntStateOf(0) }
    var memberSince by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val rows = dao.getExpensesOnce()
        expenseCount = rows.size
        totalTracked = rows.sumOf { it.amount.toIntOrNull() ?: 0 }
        memberSince = rows.mapNotNull { row -> isoToEpochDay(row.date)?.let { it to row.date } }
            .minByOrNull { it.first }?.second
    }

    LaunchedEffect(saved) {
        if (saved) { delay(2000); saved = false }
    }

    Column(
        Modifier.fillMaxSize().background(colors.surface).statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape)
                    .background(colors.surfaceCard).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_arrow_left, colors.textPrimary, 19.dp)
            }
            Text(
                "Edit Profile",
                style = t.headlineSm,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.size(116.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = t.displayHero.copy(fontSize = 46.sp),
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                name.trim().ifEmpty { "Your name" },
                style = t.headlineSm,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.clip(CircleShape).background(colors.surfaceCard)
                        .border(1.dp, colors.stroke, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    // The mockup says "256-Bit Hardware Enclave". This says what
                    // is true: the profile lives on this device and nowhere else.
                    Text("Local profile", style = t.numericSm, color = colors.textMuted)
                }
            }

            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
                    Spacer(Modifier.width(9.dp))
                    Text("Profile info", style = t.titleMd, color = colors.textPrimary)
                }

                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))

                Spacer(Modifier.height(16.dp))
                Text("FULL NAME", style = t.labelCaps, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        if (name.isEmpty()) {
                            Text("Your full name", style = t.bodyMd, color = colors.textMuted)
                        }
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it; error = null },
                            singleLine = true,
                            textStyle = t.titleMd.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    LineIcon(Res.drawable.nd_person, colors.textMuted, 17.dp)
                }

                Spacer(Modifier.height(16.dp))
                Text("PRIMARY CURRENCY", style = t.labelCaps, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                Box {
                    val selected = CurrencyConstants.byCode(currency)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
                            .clickable { currencyOpen = true }
                            .padding(horizontal = 12.dp)
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                                .background(colors.accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(selected.symbol, style = t.numericSm, color = colors.accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${selected.code} - ${selected.name}",
                            style = t.titleMd,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        LineIcon(Res.drawable.nd_chevron_down, colors.textMuted, 16.dp)
                    }
                    DropdownMenu(expanded = currencyOpen, onDismissRequest = { currencyOpen = false }) {
                        CurrencyConstants.CURRENCIES.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${option.symbol}  ${option.code} - ${option.name}",
                                        style = t.bodyMd,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = { currency = option.code; currencyOpen = false }
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = t.bodySm, color = colors.negative)
                }
                if (saved) {
                    Spacer(Modifier.height(12.dp))
                    Text("Profile updated", style = t.bodySm, color = colors.positive)
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape)
                        .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
                        .clickable {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) {
                                error = "Name cannot be empty"
                            } else {
                                // The budget is re-read rather than passed, so
                                // editing a name here can never silently reset it.
                                prefs.saveProfile(
                                    name = trimmed,
                                    monthlyBudget = prefs.getMonthlyBudget(),
                                    currencyCode = currency
                                )
                                saved = true
                            }
                        }
                        .padding(vertical = 16.dp)
                ) {
                    Text("Save changes", style = t.titleMd, color = Color.White)
                    Spacer(Modifier.width(9.dp))
                    LineIcon(Res.drawable.nd_check_circle, Color.White, 18.dp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Res.drawable.nd_analytics, colors.accent, 17.dp)
                    Spacer(Modifier.width(9.dp))
                    Text("Your activity", style = t.titleMd, color = colors.textPrimary)
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActivityStat("$expenseCount", "Expenses", Modifier.weight(1f))
                    StatDivider()
                    ActivityStat(
                        "${CurrencyConstants.symbolFor(currency)}${formatIndian(totalTracked)}",
                        "Tracked",
                        Modifier.weight(1.3f)
                    )
                    StatDivider()
                    ActivityStat(
                        memberSince?.let { prettyMonthLabel(it.substringBeforeLast("-")).substringBefore(" ") }
                            ?: "—",
                        "Member since",
                        Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colors.positive))
                    Spacer(Modifier.width(8.dp))
                    // "Cloud ledger synchronized" in the mockup. There is no
                    // cloud and nothing to synchronize.
                    Text("Stored on this device", style = t.bodySm, color = colors.textMuted)
                    Spacer(Modifier.weight(1f))
                    memberSince?.let {
                        Text(it.substringBeforeLast("-"), style = t.labelCaps, color = colors.textMuted)
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActivityStat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = Design.colors
    val t = designType()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = t.numericMd.copy(fontSize = 18.sp),
            color = colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = t.bodySm, color = colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(Design.colors.stroke))
}
