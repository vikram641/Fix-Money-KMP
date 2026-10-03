package com.fixmoney.shared.ai

import com.fixmoney.shared.platform.httpClient
import com.fixmoney.shared.platform.logWarn
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "VoiceExpenseParser"
// Same model GroqChatAssistant uses - see its comment for why this one and not
// llama-3.3-70b-versatile.
private const val GROQ_MODEL = "openai/gpt-oss-20b"

/** One spoken sentence turned into the three fields an expense row needs. */
data class ParsedVoiceExpense(
    val amount: Int,
    val category: String?,
    val note: String
)

/**
 * KMP port of the old app's `GroqVoiceExpenseParser` (OkHttp + org.json -> Ktor
 * + kotlinx.serialization). Same prompt, same model, same JSON contract.
 *
 * The old app tried `GeminiVoiceExpenseParser` first and fell back to Groq. That
 * tier used the Firebase AI SDK, which is Android-only, so only the Groq tier is
 * migrated - exactly the call already made for the chat assistant (see
 * [ChatEngine]). Plan section 6.2 has the REST route if Gemini is wanted back.
 *
 * Throws on a hard failure, as the old parser did; an amount of 0 is a *soft*
 * failure (nothing in the speech looked like money) and still returns a result
 * so the caller can say what it heard rather than losing it.
 */
object VoiceExpenseParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        httpClient().config {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    suspend fun parse(spokenText: String, categoryNames: List<String>): ParsedVoiceExpense {
        return withTimeout(20_000) { call(spokenText, categoryNames) }
    }

    private suspend fun call(
        spokenText: String,
        categoryNames: List<String>
    ): ParsedVoiceExpense {
        val systemPrompt =
            "You are parsing one short sentence someone just spoke to quickly log a single " +
                "expense into an expense-tracking app, for example: \"150 on coffee\", " +
                "\"₹500 for groceries\", \"spent 40 rupees on auto\", \"add 1200 electricity bill\".\n\n" +
                "Extract:\n" +
                "- amount: the number of rupees mentioned, as a whole number with no currency " +
                "symbol or decimals. If no number that clearly represents an amount of money is " +
                "present, use 0.\n" +
                "- category: the single best matching category from this allowed list: " +
                "${categoryNames.joinToString(", ")} - or \"NONE\" if nothing fits confidently.\n" +
                "- note: a short 2-4 word description of what the expense was for, close to the " +
                "user's own words (e.g. \"Coffee\", \"Auto fare\", \"Electricity bill\").\n\n" +
                "Respond with ONLY a JSON object shaped exactly like " +
                "{\"amount\": integer, \"category\": string, \"note\": string} - no other text, " +
                "no markdown code fences."

        val response: HttpResponse = client.post(AiProxy.CHAT_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                VoiceGroqRequest(
                    model = GROQ_MODEL,
                    temperature = 0.2,
                    responseFormat = VoiceResponseFormat("json_object"),
                    messages = listOf(
                        VoiceGroqMessage(role = "system", content = systemPrompt),
                        VoiceGroqMessage(role = "user", content = spokenText)
                    )
                )
            )
        }

        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess() || bodyText.isBlank()) {
            logWarn(TAG, "Groq call failed: ${response.status.value} $bodyText")
            error("Groq call failed: ${response.status.value}")
        }

        val content = json.decodeFromString<VoiceGroqResponse>(bodyText)
            .choices.firstOrNull()?.message?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("Groq returned no content")

        val parsed = json.decodeFromString<VoicePayload>(content)
        val category = parsed.category?.takeIf {
            it.isNotBlank() && !it.equals("NONE", ignoreCase = true)
        }

        return ParsedVoiceExpense(
            amount = parsed.amount.coerceAtLeast(0),
            category = category,
            note = parsed.note?.takeIf { it.isNotBlank() } ?: spokenText
        )
    }
}

@Serializable
private data class VoiceGroqRequest(
    val model: String,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: VoiceResponseFormat,
    val messages: List<VoiceGroqMessage>
)

@Serializable
private data class VoiceResponseFormat(val type: String)

@Serializable
private data class VoiceGroqMessage(val role: String = "", val content: String = "")

@Serializable
private data class VoiceGroqResponse(val choices: List<VoiceGroqChoice> = emptyList())

@Serializable
private data class VoiceGroqChoice(val message: VoiceGroqMessage? = null)

/** The model's own JSON reply, parsed out of the message content string. */
@Serializable
private data class VoicePayload(
    val amount: Int = 0,
    val category: String? = null,
    val note: String? = null
)
