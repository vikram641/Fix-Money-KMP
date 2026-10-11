package com.fixmoney.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fixmoney.resources.*
import com.fixmoney.shared.ai.ChatAskResult
import com.fixmoney.shared.coins.AiGate
import com.fixmoney.shared.coins.shortMessage
import com.fixmoney.ui.coins.LocalCoins
import com.fixmoney.shared.ai.ChatCategoryAmount
import com.fixmoney.shared.ai.ChatEngine
import com.fixmoney.shared.home.HomeInsight
import com.fixmoney.shared.home.HomeStateFactory
import com.fixmoney.shared.platform.currentMonthApi
import com.fixmoney.shared.platform.currentTimeLabel
import com.fixmoney.ui.Design
import com.fixmoney.ui.budget.prettyMonthLabel
import com.fixmoney.ui.components.HAIRLINE
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import com.fixmoney.ui.onboarding.formatIndian
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.koinInject

/**
 * Fixoo, rebuilt to the 2026 mockup
 * (`stitch_fixmoney_fintech_app_design/fixmoney_fixoo_copilot_chat/`).
 *
 * **Neither drop of that mockup has a usable `screen.png`** - both are 28-byte
 * `<FIFE Image failed to fetch>` placeholders - so this was built from the HTML
 * alone. Check it against a real render before treating the layout as final.
 *
 * What came across: the header with the live month pulse strip, timestamped
 * bubbles, the assistant's category breakdown, an insight card on the opening
 * message, icon'd suggestion chips with shuffle, and the composer.
 *
 * **What did not, and why.** The mockup's centrepiece is a pair of "Scenario"
 * cards - *Reallocate Runway*, *Expand Food Cap* - carrying invented figures
 * ("projected deficit ~₹1,850", "AI Confidence: 99.4%") and buttons that move
 * money between envelopes. Three separate problems: the model returns text plus
 * category totals and nothing resembling that structure, the actions do not
 * exist, and the numbers would have to be made up. Fabricated financial
 * analysis with an Apply button is not something to ship at any fidelity. The
 * card that *is* here is `HomeInsightEngine`'s, which is arithmetic over the
 * user's own rows.
 *
 * Also dropped: the overflow menu and thumb up/down (nothing behind either),
 * attach-file (no attachments), "Analyzed via HDFC Verified Feed" (no bank
 * feed), and the footer strip.
 *
 * **The mockup's footer claimed "End-to-end encrypted", which is false** - the
 * question and a summary of local spending go to Groq over HTTPS. It briefly
 * carried a truthful version of that line instead; the line was then dropped at
 * the user's request. If a footer ever returns here, it must not reinstate the
 * encryption claim.
 */
private data class Message(
    val text: String,
    val fromUser: Boolean,
    val timeLabel: String,
    val categories: List<ChatCategoryAmount> = emptyList(),
    /** Only ever set on the opening message. */
    val insight: HomeInsight? = null
)

private data class Suggestion(
    val icon: DrawableResource,
    val label: String,
    val prompt: String
)

/**
 * Chip labels are kept short on purpose - the full questions ("Am I saving
 * enough this month?") pushed the second chip off the edge on a compact phone,
 * so only one was ever readable. [Suggestion.prompt] is what actually gets
 * asked; [Suggestion.label] is only what fits.
 */
private val SUGGESTIONS = listOf(
    Suggestion(Res.drawable.nd_grocery, "Top expenses", "What are my top 3 expenses this month?"),
    Suggestion(Res.drawable.nd_savings, "Am I saving?", "Am I saving enough this month?"),
    Suggestion(Res.drawable.nd_alert_circle, "Odd charges", "Are there any unusual charges this month?"),
    Suggestion(Res.drawable.nd_analytics, "vs last month", "Compare this month to last month"),
    Suggestion(Res.drawable.nd_pdf, "Where it went", "Where did most of my money go this month?"),
    Suggestion(Res.drawable.nd_cat_food, "Food spend", "How much am I spending on food?")
)

/** How many chips are shown at once; Shuffle rotates through the rest. */
private const val VISIBLE_SUGGESTIONS = 3

@OptIn(ExperimentalResourceApi::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val colors = Design.colors
    val t = designType()
    val scope = rememberCoroutineScope()
    val engine: ChatEngine = koinInject()
    val coins = LocalCoins.current
    val homeFactory: HomeStateFactory = koinInject()

    val month = remember { currentMonthApi() }
    val homeFlow = remember { homeFactory.observe(month) }
    val home by homeFlow.collectAsStateWithLifecycle(remember { homeFactory.build(month = month) })

    val messages = remember { mutableStateListOf<Message>() }
    // Seeded once the first state arrives, so the opening message can carry the
    // real insight rather than being rebuilt when it does.
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) messages += greeting(home.insight)
    }

    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var suggestionOffset by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val avatar by rememberLottieComposition {
        LottieCompositionSpec.JsonString(Res.readBytes("files/lottie/fixoo_orb.json").decodeToString())
    }

    LaunchedEffect(messages.size, thinking) {
        val last = messages.lastIndex + if (thinking) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    fun send(question: String) {
        if (question.isBlank() || thinking) return
        // Built before the new question is added, pairing each question with the
        // reply that follows it. The old zip of "all replies" with "all questions"
        // was off by one (the greeting is a reply with no question), so the model
        // was told it had already answered the *current* question with the
        // previous answer - and repeated that answer's opening line.
        val history = buildList {
            var pending: String? = null
            for (m in messages) {
                if (m.fromUser) pending = m.text
                else pending?.let { add(it to m.text); pending = null }
            }
        }
        messages += Message(question, fromUser = true, timeLabel = currentTimeLabel())
        input = ""
        thinking = true
        scope.launch {
            val reply = when (val result = engine.ask(question, history)) {
                is ChatAskResult.Success -> Message(
                    result.answer.text,
                    fromUser = false,
                    timeLabel = currentTimeLabel(),
                    categories = result.answer.categories
                )
                ChatAskResult.QuotaExceeded -> Message(
                    "I've hit today's free-tier limit. Try again later.",
                    fromUser = false,
                    timeLabel = currentTimeLabel()
                )
                ChatAskResult.Failure -> Message(
                    "I couldn't reach the assistant just now.",
                    fromUser = false,
                    timeLabel = currentTimeLabel()
                )
                // AI coins: none of these cost anything - the Worker only charges
                // once the model has answered.
                is ChatAskResult.Blocked -> {
                    coins?.onBlocked(result.gate)
                    Message(
                        when (result.gate) {
                            // Not shortMessage(): that one says "open FixMoney",
                            // which is for the bubble and Siri - the user is in it.
                            AiGate.NoCoins ->
                                "You're out of Fixoo Coins. Watch a short video to get more - nothing was charged for this question."
                            AiGate.Busy ->
                                "I'm getting a lot of questions right now. Try again in about 30 seconds - your coin was not charged."
                            else -> result.gate.shortMessage()
                        },
                        fromUser = false,
                        timeLabel = currentTimeLabel()
                    )
                }
            }
            messages += reply
            thinking = false
        }
    }

    Column(Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        ChatHeader(
            avatarPainter = {
                Image(
                    painter = rememberLottiePainter(avatar, iterations = Compottie.IterateForever),
                    contentDescription = null,
                    // Clipped like Home's orb button: the orb's aura reaches the
                    // frame edge and shows as a faint square on the light theme.
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                )
            },
            canReset = messages.size > 1 && !thinking,
            onBack = onBack,
            onReset = {
                messages.clear()
                messages += greeting(home.insight)
                input = ""
            }
        )
        PulseStrip(
            label = prettyMonthLabel(month),
            spent = "${home.currencySymbol}${formatIndian(home.totalSpent)}",
            percentUsed = home.percentUsed,
            hasBudget = home.totalBudget > 0
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages.size) { index ->
                MessageRow(messages[index], home.currencySymbol)
            }
            if (thinking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistantBadge()
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(
                            Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Fixoo is working…", style = t.bodySm, color = colors.textMuted)
                    }
                }
            }
        }

        SuggestionRow(
            offset = suggestionOffset,
            onShuffle = { suggestionOffset = (suggestionOffset + VISIBLE_SUGGESTIONS) % SUGGESTIONS.size },
            onPick = { send(it) }
        )
        Composer(
            value = input,
            onValueChange = { input = it },
            onSend = { send(input) },
            enabled = !thinking
        )
    }
}

private fun greeting(insight: HomeInsight?) = Message(
    "Hi! I can read your local spending data and answer questions about it — " +
        "try a suggestion below or ask your own.",
    fromUser = false,
    timeLabel = currentTimeLabel(),
    insight = insight
)

/* ---------------------------------------------------------------- header */

@Composable
private fun ChatHeader(
    avatarPainter: @Composable () -> Unit,
    canReset: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    val colors = Design.colors
    val t = designType()
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceHigh)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(Res.drawable.nd_arrow_left, colors.textPrimary, 19.dp, contentDescription = "Back")
            }
            Spacer(Modifier.width(12.dp))
            avatarPainter()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fixoo", style = t.headlineSm, color = colors.textPrimary)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(CircleShape).background(colors.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text("AI", style = t.labelCaps, color = colors.accent)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.positive))
                    Spacer(Modifier.width(5.dp))
                    // "Online & Synced" in the mockup. Nothing syncs - there is
                    // no backend - so this says only what is true.
                    Text("Reads your local data", style = t.bodySm, color = colors.textMuted)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(if (canReset) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable(enabled = canReset, onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                LineIcon(
                    Res.drawable.nd_refresh,
                    if (canReset) colors.accent else colors.textMuted,
                    14.dp
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Clear",
                    style = t.numericSm,
                    color = if (canReset) colors.accent else colors.textMuted
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(HAIRLINE).background(colors.stroke))
    }
}

/** The mockup's "Sep 2026 Pulse: ₹12,480 spent • 49.9% overall cap". Real. */
@Composable
private fun PulseStrip(label: String, spent: String, percentUsed: Int, hasBudget: Boolean) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(colors.surfaceCard)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        LineIcon(Res.drawable.nd_sparkle, colors.accent, 13.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.textMuted)) { append("${label.take(3)} pulse: ") }
                withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) {
                    append("$spent spent")
                }
                if (hasBudget) {
                    withStyle(SpanStyle(color = colors.textMuted)) { append("  •  ") }
                    withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) {
                        append("$percentUsed% of cap")
                    }
                }
            },
            style = t.bodySm,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* --------------------------------------------------------------- messages */

@Composable
private fun MessageRow(message: Message, symbol: String) {
    val colors = Design.colors
    val t = designType()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.fromUser) {
                AssistantBadge()
                Spacer(Modifier.width(9.dp))
            }
            Column(
                Modifier.widthIn(max = 290.dp),
                horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start
            ) {
                Box(
                    Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (message.fromUser) 18.dp else 4.dp,
                                bottomEnd = if (message.fromUser) 4.dp else 18.dp
                            )
                        )
                        .then(
                            if (message.fromUser) {
                                Modifier.background(
                                    Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                                )
                            } else {
                                Modifier.background(colors.surfaceCard)
                                    .border(
                                        1.dp, colors.stroke,
                                        RoundedCornerShape(
                                            topStart = 18.dp, topEnd = 18.dp,
                                            bottomStart = 4.dp, bottomEnd = 18.dp
                                        )
                                    )
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            message.text,
                            style = t.bodyMd.copy(lineHeight = 21.sp),
                            color = if (message.fromUser) Color.White else colors.textPrimary
                        )
                        message.insight?.let {
                            Spacer(Modifier.height(12.dp))
                            InsightStrip(it)
                        }
                        if (message.categories.isNotEmpty()) {
                            Spacer(Modifier.height(13.dp))
                            CategoryBars(message.categories, symbol)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    message.timeLabel,
                    style = t.labelCaps,
                    color = colors.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (message.fromUser) {
                Spacer(Modifier.width(9.dp))
                UserBadge()
            }
        }
    }
}

@Composable
private fun AssistantBadge() {
    val colors = Design.colors
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(Res.drawable.nd_sparkle, colors.accent, 16.dp)
    }
}

@Composable
private fun UserBadge() {
    val colors = Design.colors
    val t = designType()
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))),
        contentAlignment = Alignment.Center
    ) {
        Text("You", style = t.labelCaps, color = Color.White)
    }
}

/**
 * The structured card on the opening message. **This is the one place the
 * mockup's "insight card" idea survives**, and it is real: every figure comes
 * from `HomeInsightEngine`, which is arithmetic over the user's own rows.
 */
@Composable
private fun InsightStrip(insight: HomeInsight) {
    val colors = Design.colors
    val t = designType()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.strokeAccent, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Res.drawable.nd_trend_up, colors.negative, 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                if (insight.hasCategoryTrend) "SPENDING SPIKE" else "PACE ALERT",
                style = t.labelCaps,
                color = colors.negative
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            buildString {
                if (insight.hasCategoryTrend) {
                    append("${insight.categoryName} is up ${insight.categoryPercentChange}% ")
                    append("on your 3-week average.")
                }
                insight.projectedOverageDay?.let {
                    if (isNotEmpty()) append(" ")
                    append("At this pace the budget runs out around day $it.")
                }
            },
            style = t.bodySm,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(colors.surfaceHigh)) {
            Box(
                Modifier.fillMaxWidth(insight.monthProgressFraction.coerceIn(0f, 1f))
                    .fillMaxHeight().clip(CircleShape).background(colors.accent)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "Day ${insight.dayOfMonth} of ${insight.daysInMonth}",
            style = t.labelCaps,
            color = colors.textMuted
        )
    }
}

/**
 * The ranked bars under an assistant reply - each category's share of the
 * largest, so the longest bar is full width and the rest read against it.
 * Colours cycle the theme's four: the assistant returns names only.
 */
@Composable
private fun CategoryBars(categories: List<ChatCategoryAmount>, symbol: String) {
    val colors = Design.colors
    val t = designType()
    val palette = listOf(
        colors.categoryFood, colors.categoryTransit,
        colors.categoryShop, colors.categoryEnt
    )
    val max = categories.maxOf { it.amount }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        categories.forEachIndexed { index, row ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(palette[index % palette.size])
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        row.name,
                        style = t.bodySm,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$symbol${formatIndian(row.amount)}",
                        style = t.numericSm,
                        color = colors.textPrimary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        .background(colors.surfaceHigh)
                ) {
                    Box(
                        Modifier.fillMaxWidth((row.amount.toFloat() / max).coerceIn(0.02f, 1f))
                            .fillMaxHeight().clip(CircleShape)
                            .background(palette[index % palette.size])
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------ suggestions + input */

@Composable
private fun SuggestionRow(offset: Int, onShuffle: () -> Unit, onPick: (String) -> Unit) {
    val colors = Design.colors
    val t = designType()
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
        ) {
            Text("Suggested", style = t.labelCaps, color = colors.textMuted)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onShuffle)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                LineIcon(Res.drawable.nd_refresh, colors.accent, 13.dp)
                Spacer(Modifier.width(5.dp))
                Text("Shuffle", style = t.labelCaps, color = colors.accent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rotates through the list rather than showing all six, which is
            // what the mockup's Shuffle implies.
            List(VISIBLE_SUGGESTIONS) { i -> SUGGESTIONS[(offset + i) % SUGGESTIONS.size] }
                .forEach { suggestion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(CircleShape)
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.stroke, CircleShape)
                            .clickable { onPick(suggestion.prompt) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        LineIcon(suggestion.icon, colors.accent, 14.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(suggestion.label, style = t.numericSm, color = colors.textPrimary, maxLines = 1)
                    }
                }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    val colors = Design.colors
    val t = designType()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clip(CircleShape)
                .background(colors.surfaceCard)
                .border(1.dp, colors.stroke, CircleShape)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Ask about your spending…", style = t.bodyMd, color = colors.textMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    textStyle = t.bodyMd.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(
                    if (enabled && value.isNotBlank()) {
                        Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd))
                    } else {
                        Brush.linearGradient(listOf(colors.surfaceHigh, colors.surfaceHigh))
                    }
                )
                .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(
                Res.drawable.nd_arrow_up,
                if (enabled && value.isNotBlank()) Color.White else colors.textMuted,
                20.dp
            )
        }
    }
}
