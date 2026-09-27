package com.fixmoney.ui.expense

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.categoryIconFor
import com.fixmoney.ui.components.MonthYearDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Port of `fragment_add_expense.xml` + `AddExpenseFragment`.
 *
 * Not yet wired: **Scan Receipt** and **Attach Photo**. Both need camera and
 * photo-library access, which is the biggest expect/actual chunk in the
 * migration plan (CameraX on Android, AVFoundation on iOS) and has no shared
 * implementation. The buttons are shown but inert, and say so when tapped.
 */
@Composable
fun AddExpenseScreen(onClose: () -> Unit) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()

    val prefs: OnboardingPrefs = koinInject()
    val categoryRepo: CategoryRepository = koinInject()
    val expenseRepo: ExpenseRepository = koinInject()

    val symbol = remember { CurrencyConstants.symbolFor(prefs.getCurrencyCode()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<Category?>(null) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(todayIso()) }
    var saving by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { categories = categoryRepo.getCategories() }

    // The old app validated with `amount.toInt()`, so whole numbers only.
    val amountValue = amount.toIntOrNull() ?: 0
    val canSave = amountValue > 0 && selected != null && !saving

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(colors.bgCard).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 19.sp, color = colors.textPrimary)
            }
            Text(
                "Add Expense",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = {
                    val category = selected ?: return@Button
                    saving = true
                    scope.launch {
                        expenseRepo.addExpense(
                            amount = amountValue,
                            currency = prefs.getCurrencyCode(),
                            dateIso = dateIso,
                            note = note.trim(),
                            category = category
                        )
                        saving = false
                        onClose()
                    }
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accent.copy(alpha = 0.4f)
                )
            ) {
                Text("SAVE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colors.cardStroke, RoundedCornerShape(20.dp))
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("A M O U N T", fontSize = 13.sp, color = colors.textSecondary)
                Spacer(Modifier.height(14.dp))
                // BasicTextField fills the available width by default, which
                // pushed the whole row to the left edge. IntrinsicSize.Min makes
                // it wrap its text so the row can actually centre.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(symbol, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    Spacer(Modifier.width(4.dp))
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (amount.isEmpty()) {
                            Text("0", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = colors.textHint)
                        }
                        BasicTextField(
                            value = amount,
                            onValueChange = { amount = it.filter { c -> c.isDigit() }.take(7) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 44.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            if (categories.isEmpty()) {
                Box(Modifier.height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp), color = colors.accent, strokeWidth = 2.dp
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        val isSelected = selected?.id == category.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(74.dp).clip(RoundedCornerShape(20.dp))
                                    .background(colors.bgCard)
                                    .border(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) colors.accent else colors.cardStroke,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selected = category },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(categoryIconFor(category.icon)),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                category.name,
                                fontSize = 13.sp,
                                color = if (isSelected) colors.accent else colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Note", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            FieldBox {
                if (note.isEmpty()) Text("Lunch at office", fontSize = 16.sp, color = colors.textHint)
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Date", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .border(1.dp, colors.cardStroke, RoundedCornerShape(18.dp))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(prettyDate(dateIso), fontSize = 16.sp, color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("📅", fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InertAction("📷  Scan Receipt", Modifier.weight(1f)) { hint = "Scan Receipt needs the camera - not migrated yet" }
                InertAction("🖼  Attach Photo", Modifier.weight(1f)) { hint = "Attach Photo needs photo access - not migrated yet" }
            }
            hint?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 13.sp, color = colors.textSecondary)
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showDatePicker) {
        MonthYearDialog(
            title = "Select Date",
            initial = dateIso,
            showDays = true,
            onDismiss = { showDatePicker = false },
            onConfirm = { dateIso = it; showDatePicker = false }
        )
    }
}

@Composable
private fun FieldBox(content: @Composable BoxScope.() -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(1.dp, colors.cardStroke, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        contentAlignment = Alignment.CenterStart,
        content = content
    )
}

@Composable
private fun InertAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, color = colors.textPrimary)
    }
}

/** "2026-09-27" -> "September 27, 2026", matching the old date field. */
internal fun prettyDate(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val m = parts[1].toIntOrNull() ?: return iso
    val d = parts[2].toIntOrNull() ?: return iso
    return "${months.getOrElse(m - 1) { parts[1] }} $d, ${parts[0]}"
}
