package com.fixmoney.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fixmoney.resources.*
import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.platform.timeLabelOf
import com.fixmoney.ui.Design
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.formatIndian
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

/**
 * Expense Detail, rebuilt to the 2026 mockup
 * (`fixmoney_expense_detail_obsidian_settings_reference`).
 *
 * The hero card, the specification rows and both actions are real. **Edit now
 * works**: it opens Add Expense in edit mode, which this redesign added - the
 * previous version of this screen deliberately offered no Edit because the
 * button had nowhere to go.
 *
 * **Dropped from the mockup**, all of them claims about data that does not
 * exist:
 *
 * - **"LEDGER SYNC"** in the header and the green dot on the icon. Nothing
 *   syncs; there is no backend.
 * - **The blue verified tick** beside the note. Nothing verifies an expense.
 * - **"Payment Account — HDFC Bank ●●●● 4092 / Active AA Sync"**. There is no
 *   bank integration and no payment-method field; that masked number is
 *   invented.
 * - **"ENCLAVE VAULT VERIFICATION • SHA-256 Validated • Zero-Knowledge Store"**.
 *   None of it is true.
 *
 * The mockup's **REF line is also gone** - removed at the user's request. It was
 * derived from the row's real primary key rather than invented, so it can come
 * back the same way if it is ever wanted.
 */
@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {}
) {
    val colors = Design.colors
    val t = designType()
    val dao: ExpenseDao = koinInject()
    val repository: ExpenseRepository = koinInject()
    val scope = rememberCoroutineScope()

    var expense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Keyed on the id so returning from an edit re-reads the row rather than
    // showing the values this screen was opened with.
    LaunchedEffect(expenseId) {
        expense = dao.getExpenseById(expenseId)
        loaded = true
    }

    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape)
                    .background(colors.surfaceCard).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_arrow_left, colors.textPrimary, 19.dp)
            }
            Text(
                "Expense Detail",
                style = t.headlineSm,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            if (expense != null) {
                Box(
                    Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f))
                        .clickable { onEdit(expenseId) },
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(Res.drawable.nd_edit, colors.accent, 18.dp)
                }
            }
        }

        val row = expense
        when {
            !loaded -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(26.dp), color = colors.accent, strokeWidth = 2.dp)
            }

            // Reachable: the list can be stale against a row deleted elsewhere.
            row == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "This expense no longer exists.",
                    style = t.bodyMd,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                val tint = categoryTint(row.categoryColor, row.categoryIcon, colors)
                val symbol = CurrencyConstants.symbolFor(row.currency)
                val amount = row.amount.toIntOrNull() ?: 0

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.stroke, RoundedCornerShape(26.dp))
                            .padding(vertical = 28.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(tint),
                            contentAlignment = Alignment.Center
                        ) {
                            LineIcon(categoryGlyph(row.categoryIcon), Color.White, 40.dp)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "-$symbol${formatIndian(amount)}",
                            style = t.displayHero.copy(fontSize = 34.sp),
                            color = colors.negative
                        )
                        if (row.note.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                row.note,
                                style = t.headlineSm,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier.clip(CircleShape)
                                .background(tint.copy(alpha = 0.15f))
                                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                row.categoryName.uppercase(),
                                style = t.labelCaps,
                                color = tint
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "TRANSACTION SPECIFICATIONS",
                        style = t.labelCaps,
                        color = colors.textMuted
                    )

                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.stroke, RoundedCornerShape(22.dp))
                    ) {
                        SpecRow(Res.drawable.nd_calendar, "Date", prettyDate(row.date))
                        SpecDivider()
                        SpecRow(Res.drawable.nd_tag, "Category", row.categoryName)
                        SpecDivider()
                        SpecRow(
                            Res.drawable.nd_rupee,
                            "Currency",
                            "${CurrencyConstants.symbolFor(row.currency)} ${row.currency}"
                        )
                        SpecDivider()
                        SpecRow(
                            Res.drawable.nd_cycle,
                            "Created",
                            prettyDate(row.createdAt),
                            // Real: `localCreatedAt` is the epoch-millis stamp
                            // written when the row was saved.
                            caption = row.localCreatedAt
                                .takeIf { it > 0 }
                                ?.let { timeLabelOf(it) }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                        .navigationBarsPadding().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
                            .clickable { onEdit(expenseId) }
                            .padding(vertical = 16.dp)
                    ) {
                        LineIcon(Res.drawable.nd_edit, colors.textPrimary, 17.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit", style = t.titleMd, color = colors.textPrimary)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(colors.negative)
                            .clickable { confirmDelete = true }
                            .padding(vertical = 16.dp)
                    ) {
                        LineIcon(Res.drawable.nd_trash, Color.White, 17.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete", style = t.titleMd, color = Color.White)
                    }
                }
            }
        }
    }

    // Deleting is the one unrecoverable action in the app, so it keeps the old
    // screen's explicit confirmation rather than an inline two-tap.
    if (confirmDelete && expense != null) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.stroke, RoundedCornerShape(26.dp))
                    .padding(22.dp)
            ) {
                Text("Delete expense", style = t.headlineSm, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    "This removes it from every total, budget and chart. It cannot be undone.",
                    style = t.bodyMd,
                    color = colors.textMuted
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(CircleShape)
                            .background(colors.surface)
                            .clickable { confirmDelete = false }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", style = t.titleMd, color = colors.textPrimary)
                    }
                    Box(
                        Modifier.weight(1f).clip(CircleShape)
                            .background(colors.negative)
                            .clickable {
                                scope.launch {
                                    repository.delete(expenseId)
                                    confirmDelete = false
                                    onBack()
                                }
                            }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Delete", style = t.titleMd, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(
    icon: DrawableResource,
    label: String,
    value: String,
    caption: String? = null
) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(icon, colors.textMuted, 17.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = t.titleMd, color = colors.textMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                value,
                style = t.titleMd,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            caption?.let {
                Text(it, style = t.bodySm, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun SpecDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .height(HAIRLINE).background(Design.colors.stroke)
    )
}
