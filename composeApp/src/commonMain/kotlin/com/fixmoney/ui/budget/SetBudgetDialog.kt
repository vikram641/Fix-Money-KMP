package com.fixmoney.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.ui.AppTheme

/**
 * The pill button a dialog's footer is built from, shared by `ExportDataDialog`
 * and `ExpenseDetailScreen`.
 *
 * This file used to hold `SetBudgetDialog` as well; `EditBudgetSheet` replaced
 * it with the 2026 design. The button stayed because those two dialogs have not
 * been redesigned yet - it moves out, or goes, when they are.
 */
@Composable
internal fun DialogButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) colors.accent else Color.Transparent)
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = if (filled) Color.Transparent else colors.cardStroke,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else colors.textPrimary
        )
    }
}
