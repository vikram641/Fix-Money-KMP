package com.fixmoney.ui.expense

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.ui.AppTheme
import com.fixmoney.ui.categoryIconFor
import com.fixmoney.ui.home.parseHexColor
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Port of `fragment_expenses.xml`: category filter chips, a search box, and
 * rows grouped by date with a per-day total.
 */
@Composable
fun ExpensesScreen() {
    val colors = AppTheme.colors
    val prefs: OnboardingPrefs = koinInject()
    val repo: ExpenseRepository = koinInject()

    val symbol = remember { CurrencyConstants.symbolFor(prefs.getCurrencyCode()) }
    val all by repo.observeExpenses().collectAsStateWithLifecycle(emptyList())

    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }

    // Chips come from whatever categories actually appear in the data, so the
    // row never offers a filter that would return nothing.
    val chips = remember(all) { listOf("All") + all.map { it.categoryName }.distinct() }

    val visible = all.filter { e ->
        (filter == "All" || e.categoryName == filter) &&
            (query.isBlank() || e.note.contains(query, true) || e.categoryName.contains(query, true))
    }
    val grouped = visible.groupBy { it.date }.toList().sortedByDescending { it.first }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Expenses", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)

        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chips) { chip ->
                val selected = chip == filter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) colors.accent else colors.cardStroke,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { filter = chip }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    if (selected) {
                        Text("✓ ", fontSize = 15.sp, color = colors.accent)
                    }
                    Text(
                        chip,
                        fontSize = 15.sp,
                        color = if (selected) colors.textPrimary else colors.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .border(1.dp, colors.cardStroke, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 15.dp)
        ) {
            Text("🔍", fontSize = 17.sp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search expenses...", fontSize = 16.sp, color = colors.textHint)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (all.isEmpty()) "No expenses yet" else "Nothing matches that",
                    fontSize = 16.sp,
                    color = colors.textSecondary
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                grouped.forEach { (date, rows) ->
                    item(key = "header-$date") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (date == todayIso()) "Today" else prettyDate(date),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "$symbol${rows.sumOf { it.amount.toIntOrNull() ?: 0 }}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green
                            )
                        }
                    }
                    items(rows, key = { it.id }) { expense -> ExpenseRow(expense, symbol) }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, symbol: String) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.bgCard)
            .border(1.dp, colors.cardStroke, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(parseHexColor(expense.categoryColor)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(categoryIconFor(expense.categoryIcon)),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                expense.note.ifBlank { expense.categoryName },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(expense.date, fontSize = 14.sp, color = colors.textSecondary)
        }
        Text(
            "-$symbol${expense.amount}",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = colors.expenseRed
        )
    }
}
