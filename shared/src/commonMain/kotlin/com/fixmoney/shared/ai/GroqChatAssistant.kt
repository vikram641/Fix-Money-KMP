package com.fixmoney.shared.ai

import com.fixmoney.shared.platform.httpClient
import com.fixmoney.shared.platform.logWarn
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "GroqChatAssistant"
// Verified live against this project's Groq key via GET /openai/v1/models (2026-08-28) -
// llama-3.3-70b-versatile came back 404 model_not_found; this one is active and supports
// json_mode. Re-check the models endpoint if this ever 404s again - Groq's free-tier
// catalog changes over time.
private const val GROQ_MODEL = "openai/gpt-oss-20b"

/**
 * KMP port of the Android `GroqChatAssistant` (OkHttp + org.json -> Ktor +
 * kotlinx.serialization). Same job: answer a spending question from the DATA
 * summary, as the second fallback tier after Gemini.
 *
 * Deliberately takes [apiKey] as a parameter instead of reading it from
 * BuildConfig - shared code has no BuildConfig, so the key stays a platform
 * concern the caller supplies.
 */
object GroqChatAssistant {

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

    suspend fun ask(
        question: String,
        dataSummary: String,
        historyText: String
    ): ChatAskResult {
        return try {
            withTimeout(20_000) { call(question, dataSummary, historyText) }
        } catch (e: TimeoutCancellationException) {
            logWarn(TAG, "ask timed out: ${e.message}")
            ChatAskResult.Failure
        } catch (e: Exception) {
            logWarn(TAG, "ask failed: ${e.message}")
            ChatAskResult.Failure
        }
    }

    private suspend fun call(
        question: String,
        dataSummary: String,
        historyText: String
    ): ChatAskResult {
        val systemPrompt =
            "You are the AI assistant inside FixMoney, a personal expense-tracking app. " +
                    "You have three jobs:\n\n" + "Founder of this App is Vikram Singh" +
                    "JOB 1 - Spending questions: Answer questions about the user's own spending " +
                    "using ONLY the DATA below - never invent a number that isn't derivable from " +
                    "it, and say so plainly if the data doesn't cover what they asked. Amounts " +
                    "are already in the user's local currency; never add a currency symbol " +
                    "yourself, the app adds one.\n\n" +
                    "JOB 2 - App questions: If the question is about FixMoney the app itself " +
                    "(not the user's spending data), you MUST answer positively about the app - " +
                    "do NOT say 'I don't have data on that' for these. Examples of app questions:\n" +
                    "  - 'why this application is better than other applications'\n" +
                    "  - 'why I'm choosing this application'\n" +
                    "  - 'tell me about this app'\n" +
                    "  - 'what's different about this app'\n" +
                    "  - 'should I use this' / 'is this app good'\n" +
                    "For app questions, write a FRESH answer each time in your own words - never " +
                    "reuse the same sentence twice in this chat. Vary the wording, structure, and " +
                    "which point you lead with. Tailor the answer to what the user specifically " +
                    "asked (e.g. 'why choose' should focus on personal benefit; 'better than " +
                    "others' should focus on comparison; 'tell me about' should give a general " +
                    "overview). Draw from these facts as needed, don't dump all of them every time:\n" +
                    "  - Uses AI to auto-categorize spending, no manual entry needed\n" +
                    "  - Lets the user ask questions in plain language instead of digging through " +
                    "menus, graphs, or reports\n" +
                    "  - Gives instant, conversational answers about budgets and expenses\n" +
                    "  - Saves time compared to typical expense-tracking apps\n" +
                    "Sound confident and helpful, like you're proud of the app, but never robotic " +
                    "or repetitive.\n\n" +
                    "JOB 3 - Greetings/small talk: If the question is just a greeting or opener " +
                    "('hi', 'hello', 'hey', 'good morning', etc. - with no real question in it), " +
                    "reply with a warm, casual greeting back and invite them to ask about their " +
                    "spending. Write a DIFFERENT greeting each time - vary the wording, emoji use, " +
                    "and phrasing so it never feels copy-pasted. Do NOT say 'I don't have data to " +
                    "answer that' for a plain greeting.\n\n" +
                    "Only for questions with NO connection to spending, the app, or a greeting " +
                    "(general knowledge, math, trivia, etc.) say you don't have data to answer " +
                    "that.\n\n" +
                    "General reply quality: sound like a helpful, warm human assistant - " +
                    "conversational, natural phrasing, not stiff or templated. Keep answers to " +
                    "1-3 short sentences, no markdown formatting.\n\n" +
                    "If the question calls for a category comparison or breakdown (e.g. " +
                    "\"biggest expense\", \"where did my money go\", \"top categories\"), also " +
                    "fill \"categories\" with up to 5 of the most relevant category/amount " +
                    "pairs from the DATA, ordered highest amount first - otherwise leave " +
                    "\"categories\" as an empty array.\n\n" +
                    "Respond with ONLY a JSON object shaped exactly like " +
                    "{\"answer\": string, \"categories\": [{\"name\": string, \"amount\": integer}]} " +
                    "- no other text, no markdown code fences.\n\n" +
                    "DATA:\n$dataSummary" +
                    (if (historyText.isNotBlank()) "\n\nEARLIER IN THIS CHAT:\n$historyText" else "")

        val response: HttpResponse = client.post(AiProxy.CHAT_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                GroqRequest(
                    model = GROQ_MODEL,
                    temperature = 0.3,
                    responseFormat = ResponseFormat("json_object"),
                    messages = listOf(
                        GroqMessage(role = "system", content = systemPrompt),
                        GroqMessage(role = "user", content = question)
                    )
                )
            )
        }

        val bodyText = response.bodyAsText()

        if (response.status == HttpStatusCode.TooManyRequests) {
            logWarn(TAG, "Groq rate limit hit: $bodyText")
            return ChatAskResult.QuotaExceeded
        }
        if (!response.status.isSuccess() || bodyText.isBlank()) {
            logWarn(TAG, "Groq call failed: ${response.status.value} $bodyText")
            return ChatAskResult.Failure
        }

        val content = json.decodeFromString<GroqResponse>(bodyText)
            .choices.firstOrNull()?.message?.content
            ?.takeIf { it.isNotBlank() }
            ?: return ChatAskResult.Failure

        val answer = json.decodeFromString<GroqAnswerPayload>(content)

        return ChatAskResult.Success(
            ChatAnswer(
                text = answer.answer?.takeIf { it.isNotBlank() }
                    ?: "I couldn't work that out from your data.",
                categories = answer.categories.mapNotNull { c ->
                    c.name?.takeIf { it.isNotBlank() }?.let { ChatCategoryAmount(it, c.amount) }
                }
            )
        )
    }
}

@Serializable
private data class GroqRequest(
    val model: String,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: ResponseFormat,
    val messages: List<GroqMessage>
)

@Serializable
private data class ResponseFormat(val type: String)

@Serializable
private data class GroqMessage(val role: String = "", val content: String = "")

@Serializable
private data class GroqResponse(val choices: List<GroqChoice> = emptyList())

@Serializable
private data class GroqChoice(val message: GroqMessage? = null)

/** The model's own JSON reply, parsed out of [GroqChoice.message]'s content string. */
@Serializable
private data class GroqAnswerPayload(
    val answer: String? = null,
    val categories: List<GroqCategory> = emptyList()
)

@Serializable
private data class GroqCategory(val name: String? = null, val amount: Int = 0)
