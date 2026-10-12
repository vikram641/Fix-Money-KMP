package com.fixmoney.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.Dialog
import com.fixmoney.resources.*
import com.fixmoney.shared.export.ExpenseExporter
import com.fixmoney.shared.export.ExportFileWriter
import com.fixmoney.shared.export.ExportLocation
import com.fixmoney.shared.export.ExportRange
import com.fixmoney.shared.export.ExportResult
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.isoToEpochDay
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.ui.Design
import com.fixmoney.ui.budget.prettyMonthLabel
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.designType
import com.fixmoney.ui.expense.prettyDate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

/**
 * Export Data, rebuilt to the 2026 mockup
 * (`fixmoney_export_data_modal_obsidian_theme`).
 *
 * **Only CSV is selectable.** The mockup offers CSV / Excel / PDF; this build
 * writes CSV and nothing else. The other two are shown - the user asked for the
 * full layout on Settings - but they are **disabled with a SOON pill** rather
 * than live, because unlike a preference toggle, picking a format that does not
 * exist either produces the wrong file or fails at the end of the flow. A
 * format is a promise about the artefact, not a stored preference.
 *
 * The mockup's footer ("Zero-Knowledge Export", "SHA-256 Validated", "audited")
 * is **not** reproduced: nothing here is audited, hashed or zero-knowledge. The
 * strip says where the file lands instead, which is what the user needs to know.
 */
@Composable
fun ExportDataDialog(onDismiss: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val exporter: ExpenseExporter = koinInject()
    val writer: ExportFileWriter = koinInject()
    val scope = rememberCoroutineScope()

    var monthly by remember { mutableStateOf(true) }
    var month by remember { mutableStateOf(currentMonthApi()) }
    var fromIso by remember { mutableStateOf<String?>(null) }
    var toIso by remember { mutableStateOf<String?>(null) }

    var picking by remember { mutableStateOf<Picker?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteIsError by remember { mutableStateOf(false) }
    // Set only on success, so the "Open" action appears exactly when there is a
    // file to open.
    var written by remember { mutableStateOf<ExportLocation?>(null) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.strokeAccent, RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                        .background(colors.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(Res.drawable.nd_download, colors.accent, 19.dp)
                }
                Spacer(Modifier.width(11.dp))
                Text("SPENDING REPORT", style = t.labelCaps, color = colors.accent)
            }

            Spacer(Modifier.height(14.dp))
            Text("Export ledger & data", style = t.headlineLg.copy(fontSize = 24.sp), color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose a period. The file is a CSV, which opens in Excel, Numbers or Sheets.",
                style = t.bodyMd,
                color = colors.textMuted
            )

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(colors.surface).padding(4.dp)
            ) {
                ScopeTab("Monthly", Res.drawable.nd_calendar, monthly, Modifier.weight(1f)) {
                    monthly = true; note = null
                }
                ScopeTab("Custom range", Res.drawable.nd_cycle, !monthly, Modifier.weight(1f)) {
                    monthly = false; note = null
                }
            }

            Spacer(Modifier.height(14.dp))
            if (monthly) {
                PeriodRow(
                    label = "BILLING PERIOD",
                    value = prettyMonthLabel(month),
                    onChange = { picking = Picker.Month }
                )
            } else {
                PeriodRow(
                    label = "FROM",
                    value = fromIso?.let { prettyDate(it) } ?: "Pick a date",
                    onChange = { picking = Picker.From }
                )
                Spacer(Modifier.height(10.dp))
                PeriodRow(
                    label = "TO",
                    value = toIso?.let { prettyDate(it) } ?: "Pick a date",
                    onChange = { picking = Picker.To }
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("FILE FORMAT", style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FormatTile("CSV", "Raw data", Res.drawable.nd_pdf, available = true, selected = true, modifier = Modifier.weight(1f))
                FormatTile("Excel", ".xlsx", Res.drawable.nd_glass, available = false, selected = false, modifier = Modifier.weight(1f))
                FormatTile("PDF", "Report", Res.drawable.nd_pdf, available = false, selected = false, modifier = Modifier.weight(1f))
            }

            note?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    style = t.bodySm,
                    color = if (noteIsError) colors.negative else colors.positive
                )
            }

            // Offered only when a file actually exists and the platform can hand
            // it over - `open` returns false on iOS, where sharing needs a
            // UIViewController that shared code has no access to.
            written?.let { location ->
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f))
                        .clickable { writer.open(location) }
                        .padding(vertical = 13.dp)
                ) {
                    LineIcon(Res.drawable.nd_arrow_outward, colors.accent, 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Open with…", style = t.numericSm, color = colors.accent)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(colors.surface)
                        .clickable(enabled = !busy, onClick = onDismiss)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", style = t.titleMd, color = colors.textPrimary)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp), color = colors.accent, strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                                )
                                .clickable {
                                    val range = buildRange(monthly, month, fromIso, toIso) { message ->
                                        note = message
                                        noteIsError = true
                                    } ?: return@clickable
                                    busy = true
                                    note = null
                                    scope.launch {
                                        when (val result = exporter.export(range)) {
                                            is ExportResult.Success -> {
                                                note = result.message
                                                noteIsError = false
                                                written = result.location
                                            }
                                            is ExportResult.Failure -> {
                                                note = result.message
                                                noteIsError = true
                                                written = null
                                            }
                                        }
                                        busy = false
                                    }
                                }
                                .padding(vertical = 15.dp)
                        ) {
                            LineIcon(Res.drawable.nd_download, Color.White, 17.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("Export", style = t.titleMd, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    picking?.let { which ->
        MonthYearDialog(
            title = if (which == Picker.Month) "Select month" else "Select date",
            initial = when (which) {
                Picker.Month -> month
                Picker.From -> fromIso ?: todayIso()
                Picker.To -> toIso ?: todayIso()
            },
            showDays = which != Picker.Month,
            onDismiss = { picking = null },
            onConfirm = { value ->
                when (which) {
                    Picker.Month -> month = value
                    Picker.From -> fromIso = value
                    Picker.To -> toIso = value
                }
                note = null
                picking = null
            }
        )
    }
}

private enum class Picker { Month, From, To }

@Composable
private fun ScopeTab(
    label: String,
    icon: DrawableResource,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp)
    ) {
        LineIcon(icon, if (selected) Color.White else colors.textMuted, 15.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = t.numericSm,
            color = if (selected) Color.White else colors.textMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun PeriodRow(label: String, value: String, onChange: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onChange)
            .padding(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                .background(colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(Res.drawable.nd_calendar, colors.accent, 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = t.titleMd,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("Change", style = t.numericSm, color = colors.accent)
        Spacer(Modifier.width(4.dp))
        LineIcon(Res.drawable.nd_chevron_down, colors.accent, 15.dp)
    }
}

/**
 * [available] false renders the tile dimmed with a SOON pill and swallows taps.
 * See this file's doc comment for why a format is not treated like a toggle.
 */
@Composable
private fun FormatTile(
    name: String,
    subtitle: String,
    icon: DrawableResource,
    available: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = Design.colors
    val t = designType()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.accent else colors.stroke,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LineIcon(icon, if (available) colors.accent else colors.textMuted, 18.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = t.titleMd,
            color = if (available) colors.textPrimary else colors.textMuted,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        if (available) {
            Text(subtitle, style = t.bodySm, color = colors.textMuted, maxLines = 1)
        } else {
            Box(
                Modifier.clip(CircleShape)
                    .background(colors.textMuted.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("SOON", style = t.labelCaps, color = colors.textMuted)
            }
        }
    }
}

/** Null means the inputs were rejected; [onError] has already said why. */
private fun buildRange(
    monthly: Boolean,
    month: String,
    fromIso: String?,
    toIso: String?,
    onError: (String) -> Unit
): ExportRange? {
    if (monthly) return ExportRange.Monthly(month, prettyMonthLabel(month))
    if (fromIso == null || toIso == null) {
        onError("Pick both a from and to date")
        return null
    }
    val from = isoToEpochDay(fromIso)
    val to = isoToEpochDay(toIso)
    if (from == null || to == null || from > to) {
        onError("From date must be before to date")
        return null
    }
    return ExportRange.Custom(fromIso, toIso)
}
