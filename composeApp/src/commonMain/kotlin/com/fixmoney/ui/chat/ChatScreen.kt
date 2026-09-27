package com.fixmoney.ui.chat

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.resources.Res
import com.fixmoney.shared.ai.ChatAskResult
import com.fixmoney.shared.ai.ChatEngine
import com.fixmoney.ui.AppTheme
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.koinInject

private data class Message(val text: String, val fromUser: Boolean)

private val SUGGESTIONS = listOf(
    "Am I over budget?",
    "Biggest expense this week?",
    "Compare this month to last"
)

/** Port of `fragment_chat.xml` - the "Fixoo" assistant. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val engine: ChatEngine = koinInject()

    val messages = remember {
        mutableStateListOf(
            Message(
                "Hi! Ask me anything about your spending — try a suggestion below or type your own question.",
                fromUser = false
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    val avatar by rememberLottieComposition {
        LottieCompositionSpec.JsonString(Res.readBytes("files/lottie/ai.json").decodeToString())
    }

    fun send(question: String) {
        if (question.isBlank() || thinking) return
        messages += Message(question, fromUser = true)
        input = ""
        thinking = true
        scope.launch {
            val history = messages.filter { !it.fromUser }.zip(messages.filter { it.fromUser })
                .map { (a, q) -> q.text to a.text }
            val reply = when (val result = engine.ask(question, history)) {
                is ChatAskResult.Success -> result.answer.text
                ChatAskResult.QuotaExceeded -> "I've hit today's free-tier limit. Try again later."
                ChatAskResult.Failure -> "I couldn't reach the assistant just now."
            }
            messages += Message(reply, fromUser = false)
            thinking = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .border(1.dp, colors.cardStroke, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 22.sp, color = colors.textPrimary) }
            Spacer(Modifier.width(14.dp))
            Image(
                painter = rememberLottiePainter(avatar, iterations = Compottie.IterateForever),
                contentDescription = null,
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Fixoo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("AI expense assistant", fontSize = 14.sp, color = colors.textSecondary)
            }
        }
        HorizontalDivider(color = colors.cardStroke)

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier.widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (message.fromUser) colors.accent else colors.bgCard)
                            .border(
                                1.dp,
                                if (message.fromUser) Color.Transparent else colors.cardStroke,
                                RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            message.text,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            color = if (message.fromUser) Color.White else colors.textPrimary
                        )
                    }
                }
            }
            if (thinking) {
                item {
                    CircularProgressIndicator(
                        Modifier.size(22.dp), color = colors.accent, strokeWidth = 2.dp
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(SUGGESTIONS) { suggestion ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(colors.bgCard)
                        .clickable { send(suggestion) }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(suggestion, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                    .border(1.dp, colors.cardStroke, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                if (input.isEmpty()) {
                    Text("Ask about your spending…", fontSize = 16.sp, color = colors.textHint)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.accent)
                    .clickable { send(input) },
                contentAlignment = Alignment.Center
            ) { Text("↑", fontSize = 22.sp, color = Color.White) }
        }
    }
}
