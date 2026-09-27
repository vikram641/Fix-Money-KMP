package com.fixmoney.ui.budget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fixmoney.shared.model.Category
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.categoryIconFor
import org.jetbrains.compose.resources.painterResource

/** Port of the old app's Set Budget dialog: pick a category, type a limit. */
@Composable
fun SetBudgetDialog(
    categories: List<Category>,
    monthLabel: String,
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (Category, Int) -> Unit
) {
    val colors = AppTheme.colors
    var selected by remember { mutableStateOf(categories.firstOrNull()) }
    var limit by remember { mutableStateOf("") }
    val amount = limit.toIntOrNull() ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(colors.bgCard).padding(22.dp)
        ) {
            Text("Set Budget", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)

            Spacer(Modifier.height(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(categories, key = { it.id }) { category ->
                    val isSelected = selected?.id == category.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) colors.accent.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .clickable { selected = category }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(categoryIconFor(category.icon)),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            category.name,
                            fontSize = 14.sp,
                            color = if (isSelected) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                monthLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))
            Text("Limit Amount", fontSize = 14.sp, color = colors.textSecondary)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (limit.isEmpty()) {
                    Text("0", fontSize = 20.sp, color = colors.textHint)
                }
                BasicTextField(
                    value = limit,
                    onValueChange = { limit = it.filter { c -> c.isDigit() }.take(7) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(color = colors.cardStroke, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .border(1.dp, colors.cardStroke, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(currencyCode, fontSize = 18.sp, color = colors.textPrimary)
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DialogButton("Cancel", Modifier.weight(1f), filled = false, onClick = onDismiss)
                DialogButton("Save Budget", Modifier.weight(1f), filled = true, enabled = amount > 0) {
                    selected?.let { onSave(it, amount) }
                }
            }
        }
    }
}

@Composable
internal fun DialogButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier.clip(CircleShape)
            .background(
                when {
                    !filled -> colors.inputBg
                    enabled -> colors.accent
                    else -> colors.accent.copy(alpha = 0.4f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
            color = if (filled) Color.White else colors.textSecondary
        )
    }
}
