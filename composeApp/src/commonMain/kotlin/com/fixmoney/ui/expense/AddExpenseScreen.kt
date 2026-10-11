package com.fixmoney.ui.expense

import com.fixmoney.shared.db.parseStoredAmount

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.fixmoney.shared.config.FeatureFlag
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.todayIso
import com.fixmoney.shared.receipt.ExtractedReceipt
import com.fixmoney.ui.Design
import com.fixmoney.ui.categoryGlyph
import com.fixmoney.ui.categoryTint
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.components.MonthYearDialog
import com.fixmoney.ui.designType
import com.fixmoney.ui.isEnabled
import com.fixmoney.ui.home.relativeDay
import com.fixmoney.ui.onboarding.formatIndian
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

/**
 * Add Expense, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design/fixmoney_add_expense/`).
 *
 * A sheet: big amount, a 4-across category grid, note and date, the mockup's
 * **own numeric keypad**, and the Add button. The keypad is the reason the
 * system keyboard never opens here - the only free-text field is the note.
 *
 * **Deliberate differences:**
 *
 * - **No account / payment-method chips** ("HDFC Bank ••4092", "UPI"). There is
 *   no bank integration and no payment-method field on the expense; the masked
 *   account number in the mockup is invented. Same call as Settings' Connected
 *   Accounts, which the user asked to show as an empty state rather than fake.
 * - **The keypad's "." is a "00" key.** `ExpenseEntity.amount` is a String that
 *   every screen reads with `toIntOrNull()`, so storing "450.50" would make the
 *   row count as **zero** everywhere - in totals, budgets and analytics. Paise
 *   need that column handled properly first; until then the decimal point would
 *   be a trap. "00" is the useful key for rupee amounts anyway.
 * - **Attach Photo is gone**, not merely inert. It had no listener in the old
 *   app either (see git history of this file); the mockup does not have it; a
 *   button that has never done anything should not survive a redesign.
 */
@Composable
fun AddExpenseScreen(
    onClose: () -> Unit,
    onScanReceipt: () -> Unit = {},
    scanned: ExtractedReceipt? = null,
    onScanConsumed: () -> Unit = {},
    /** Non-null turns this into an edit of that row rather than a new expense. */
    editingId: String? = null
) {
    val colors = Design.colors
    val t = designType()
    val scope = rememberCoroutineScope()

    val prefs: OnboardingPrefs = koinInject()
    val categoryRepo: CategoryRepository = koinInject()
    val expenseRepo: ExpenseRepository = koinInject()
    val expenseDao: ExpenseDao = koinInject()

    val profileCurrency = remember { prefs.getCurrencyCode() }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<Category?>(null) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(todayIso()) }
    // The currency printed on a scanned receipt, when it differs from the profile's.
    var scannedForeignCurrency by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // The row being edited, once loaded. Held whole so `updateExpense` can carry
    // over the fields this screen does not touch (createdAt, localCreatedAt).
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }

    // Past notes, for the quick-fill chips. Read once from the rows the app
    // already holds rather than through a new query: this is a personal expense
    // log, and `getExpensesOnce()` is what Export and Edit Profile use too.
    var pastNoted by remember { mutableStateOf<List<ExpenseEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        categories = categoryRepo.getCategories()
        pastNoted = expenseDao.getExpensesOnce().filter { it.note.isNotBlank() }
    }

    // Prefill, keyed on the categories as well: the chip the row belongs to can
    // only be selected once the list has arrived.
    LaunchedEffect(editingId, categories) {
        val id = editingId ?: return@LaunchedEffect
        val row = editing ?: expenseDao.getExpenseById(id) ?: return@LaunchedEffect
        if (editing == null) {
            editing = row
            // Rounded, not digit-stripped: "12.50" from the old app became
            // "1250" here, and saving the edit wrote 100x the real amount.
            amount = parseStoredAmount(row.amount).takeIf { it > 0 }?.toString()?.take(7).orEmpty()
            note = row.note
            dateIso = row.date
        }
        if (selected == null && categories.isNotEmpty()) {
            selected = categories.firstOrNull { it.id == row.categoryId }
                ?: categories.firstOrNull { it.name.equals(row.categoryName, ignoreCase = true) }
        }
    }

    val noteSuggestions = remember(pastNoted, selected, note) {
        noteSuggestionsFor(pastNoted, selected?.id)
            // Hide a chip the note already matches - tapping it would do nothing.
            .filterNot { it.equals(note.trim(), ignoreCase = true) }
            .take(SUGGESTION_LIMIT)
    }

    // Keyed on the categories too: a scan can land before they finish loading,
    // and the old app needed a `pendingScanResult` field for that case.
    // Re-running when the list arrives covers it without the extra state.
    LaunchedEffect(scanned, categories) {
        // A scan is for a *new* expense; applying one on top of a row being
        // edited would silently rewrite it from a photo.
        if (editingId != null) return@LaunchedEffect
        val result = scanned ?: return@LaunchedEffect
        result.amount?.let { amount = parseStoredAmount(it).takeIf { v -> v > 0 }?.toString()?.take(7).orEmpty() }
        result.note?.let { note = it }
        // A ₹ bill scanned on a $ profile was saved as $ without a word.
        scannedForeignCurrency = result.currency?.takeIf { !it.equals(profileCurrency, ignoreCase = true) }
        result.date?.let { dateIso = it }

        val name = result.category
        if (name != null && categories.isNotEmpty()) {
            // Exact name first, then either-contains - the old app's two-step
            // match, which saves a "Food & Drink" vs "Food" style near-miss.
            val match = categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: categories.firstOrNull { cat ->
                    cat.name.contains(name, ignoreCase = true) ||
                        name.contains(cat.name, ignoreCase = true)
                }
            if (match != null) selected = match
        }
        // Only give up ownership once there is nothing left to wait for.
        if (name == null || categories.isNotEmpty()) onScanConsumed()
    }

    val symbol = CurrencyConstants.symbolFor(editing?.currency ?: profileCurrency)
    val amountValue = amount.toIntOrNull() ?: 0
    val canSave = amountValue > 0 && selected != null && !saving

    fun press(key: String) {
        amount = when (key) {
            BACKSPACE -> amount.dropLast(1)
            else -> (amount + key).trimStart('0').take(7)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surfaceCard)
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape).background(colors.surfaceHigh)
            )

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape)
                        .background(colors.surface).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 16.sp, color = colors.textPrimary)
                }
                Text(
                    if (editingId != null) "Edit Expense" else "Add Expense",
                    style = t.headlineSm,
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                // Scanning replaces every field, which makes no sense while
                // editing one row - so the button is only there when adding.
                if (editingId == null && isEnabled(FeatureFlag.ReceiptScan)) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f))
                            .clickable(onClick = onScanReceipt),
                        contentAlignment = Alignment.Center
                    ) {
                        LineIcon(Res.drawable.nd_scan_doc, colors.accent, 19.dp)
                    }
                }
            }

            // Everything above the keypad scrolls, so a small screen can still
            // reach the category grid without the keypad moving.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        symbol,
                        style = t.displayHero.copy(fontSize = 30.sp),
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (amount.isEmpty()) "0" else formatIndian(amountValue),
                        style = t.displayHero.copy(fontSize = 46.sp),
                        color = if (amount.isEmpty()) colors.textMuted else colors.textPrimary
                    )
                    // The mockup's caret. Static rather than blinking: a cursor
                    // that blinks next to a keypad-driven field reads as a text
                    // field the system keyboard should open for, and it should
                    // not.
                    Box(
                        Modifier.padding(start = 3.dp, bottom = 8.dp)
                            .size(width = 2.dp, height = 34.dp)
                            .background(colors.accent)
                    )
                }
                scannedForeignCurrency?.let { code ->
                    Text(
                        "This receipt is in $code but your profile is in $profileCurrency. " +
                            "It will be saved as $profileCurrency - check the amount before saving.",
                        style = t.bodySm,
                        color = colors.negative,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SELECT CATEGORY", style = t.labelCaps, color = colors.textMuted)
                    Spacer(Modifier.weight(1f))
                    selected?.let { category ->
                        Text(
                            category.name,
                            style = t.numericSm,
                            color = categoryTint(category.color, category.icon, colors)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (categories.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp), color = colors.accent, strokeWidth = 2.dp
                        )
                    }
                } else {
                    CategoryGrid(categories, selected) { selected = it }
                }

                if (noteSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    NoteSuggestions(noteSuggestions) { note = it }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NoteField(note, Modifier.weight(1.4f)) { note = it }
                    DateField(dateIso, Modifier.weight(1f)) { showDatePicker = true }
                }
                Spacer(Modifier.height(14.dp))
            }

            Keypad(onKey = ::press)

            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .padding(top = 6.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSave) Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                        else Brush.linearGradient(
                            listOf(colors.surfaceHigh, colors.surfaceHigh)
                        )
                    )
                    .clickable(enabled = canSave) {
                        val category = selected ?: return@clickable
                        saving = true
                        scope.launch {
                            val existing = editing
                            if (existing != null) {
                                expenseRepo.updateExpense(
                                    existing = existing,
                                    amount = amountValue,
                                    // The row keeps the currency it was recorded in;
                                    // relabelling it on edit turned ₹ into $.
                                    currency = existing.currency,
                                    dateIso = dateIso,
                                    note = note.trim(),
                                    category = category
                                )
                            } else {
                                expenseRepo.addExpense(
                                    amount = amountValue,
                                    currency = prefs.getCurrencyCode(),
                                    dateIso = dateIso,
                                    note = note.trim(),
                                    category = category
                                )
                            }
                            saving = false
                            onClose()
                        }
                    }
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(
                        Res.drawable.nd_check_circle,
                        if (canSave) Color.White else colors.textMuted,
                        19.dp
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        addLabel(canSave, selected, amountValue, symbol, editingId != null),
                        style = t.titleMd,
                        color = if (canSave) Color.White else colors.textMuted
                    )
                }
            }
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

/** Says what is actually missing rather than staying "Add ₹0" and inert. */
private fun addLabel(
    canSave: Boolean,
    selected: Category?,
    amount: Int,
    symbol: String,
    editing: Boolean
): String = when {
    amount <= 0 -> "Enter an amount"
    selected == null -> "Pick a category"
    editing -> "Save changes"
    else -> "Add $symbol${formatIndian(amount)}.00"
}

/* -------------------------------------------------------- category grid */

@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Four across, as the mockup. A plain chunked Column rather than a
        // LazyVerticalGrid: this sits inside a vertically scrolling parent, and
        // nesting a lazy grid there needs a fixed height it cannot know.
        categories.chunked(COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        isSelected = category.id == selected?.id,
                        modifier = Modifier.weight(1f)
                    ) { onSelect(category) }
                }
                // Keeps the last row's tiles the same width as a full one.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val COLUMNS = 4

@Composable
private fun CategoryTile(
    category: Category,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = Design.colors
    val t = designType()
    val tint = categoryTint(category.color, category.icon, colors)

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) tint.copy(alpha = 0.12f) else colors.surface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) tint else colors.stroke,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(categoryGlyph(category.icon), tint, 19.dp)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            category.name,
            style = t.bodySm,
            color = if (isSelected) colors.textPrimary else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/* --------------------------------------------------------- note and date */

/** How many chips fit comfortably on one scrolling row. */
private const val SUGGESTION_LIMIT = 8

/**
 * Quick-fill chips for the note - "Cab", "Domino's", whatever this user
 * actually types.
 *
 * **Ranked from their own history, not a canned brand list.** The notes that
 * matter are the ones they write, and a hardcoded set of merchants would be
 * arbitrary (why Domino's and not the shop next door?) and wrong outside India.
 * Ordering is by how often a note has been used, then how recently.
 *
 * When a category is selected its own notes come first, because "Cab" belongs
 * under Transport and "Domino's" under Food; the rest of the history tops the
 * row up so it is never half empty. [SEED_NOTES] only ever shows on a fresh
 * install, where there is no history to rank.
 */
internal fun noteSuggestionsFor(
    pastNoted: List<ExpenseEntity>,
    categoryId: String?
): List<String> {
    if (pastNoted.isEmpty()) return SEED_NOTES

    fun rank(rows: List<ExpenseEntity>): List<String> =
        rows.groupBy { it.note.trim() }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<ExpenseEntity>>> { it.value.size }
                    .thenByDescending { entry -> entry.value.maxOf { it.localCreatedAt } }
            )
            .map { it.key }

    val forCategory = if (categoryId == null) emptyList()
    else rank(pastNoted.filter { it.categoryId == categoryId })

    // `distinct` after the concat, so a note that is top of both lists appears
    // once and keeps its leading position.
    return (forCategory + rank(pastNoted)).distinct()
}

/** Shown only when there is no history at all. Deliberately generic. */
private val SEED_NOTES = listOf(
    "Cab", "Coffee", "Lunch", "Groceries", "Fuel", "Recharge", "Medicines", "Rent"
)

@Composable
private fun NoteSuggestions(suggestions: List<String>, onPick: (String) -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.stroke, CircleShape)
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                LineIcon(Res.drawable.nd_tag, colors.textMuted, 13.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    suggestion,
                    style = t.bodySm,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NoteField(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp)
    ) {
        LineIcon(Res.drawable.nd_tag, colors.textMuted, 16.dp)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Add a note", style = t.bodyMd, color = colors.textMuted, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = t.bodyMd.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DateField(dateIso: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        LineIcon(Res.drawable.nd_calendar, colors.textMuted, 16.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            relativeDay(dateIso),
            style = t.bodyMd,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ----------------------------------------------------------------- keypad */

private const val BACKSPACE = "⌫"

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        // "00" where the mockup has "." - see this file's doc comment.
        listOf("00", "0", BACKSPACE)
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key -> KeypadKey(key, Modifier.weight(1f), onKey) }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    val colors = Design.colors
    val t = designType()
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable { onKey(key) },
        contentAlignment = Alignment.Center
    ) {
        if (key == BACKSPACE) {
            LineIcon(Res.drawable.nd_backspace, colors.textMuted, 20.dp, contentDescription = "Delete digit")
        } else {
            Text(key, style = t.headlineSm.copy(fontSize = 20.sp), color = colors.textPrimary)
        }
    }
}

/** "2026-09-27" -> "September 27, 2026", still used by Expense Detail. */
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
