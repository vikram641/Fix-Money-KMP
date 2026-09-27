package com.fixmoney.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.budget.DialogButton

/**
 * Port of the old Export Data dialog. **Export itself is not wired**: writing a
 * CSV and handing it to a share sheet is platform work (SAF on Android, a
 * UIActivityViewController on iOS) with no shared implementation yet.
 */
@Composable
fun ExportDataDialog(onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    var monthly by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(colors.bgCard).padding(22.dp)
        ) {
            Text("Export Data", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Choose a period to export as a spreadsheet (CSV, opens in Excel).",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = colors.textSecondary
            )

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(colors.inputBg).padding(4.dp)
            ) {
                SegmentButton("Monthly", monthly, Modifier.weight(1f)) { monthly = true }
                SegmentButton("Custom Range", !monthly, Modifier.weight(1f)) { monthly = false }
            }

            note?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, fontSize = 14.sp, color = colors.textSecondary)
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DialogButton("Cancel", Modifier.weight(1f), filled = false, onClick = onDismiss)
                DialogButton("Export", Modifier.weight(1f), filled = true) {
                    note = "Export needs file sharing, which isn't migrated yet."
                }
            }
        }
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier.clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else colors.textPrimary
        )
    }
}
