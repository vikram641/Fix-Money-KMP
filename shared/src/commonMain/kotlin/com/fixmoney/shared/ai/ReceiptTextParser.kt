package com.fixmoney.shared.ai

import com.fixmoney.shared.coins.AiBlockedException
import com.fixmoney.shared.coins.AiFeature
import com.fixmoney.shared.coins.AiGate
import com.fixmoney.shared.coins.WalletRepository
import com.fixmoney.shared.platform.httpClient
import com.fixmoney.shared.platform.logWarn
import com.fixmoney.shared.receipt.ExtractedReceipt
import com.fixmoney.shared.receipt.ReceiptImageStatus
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "ReceiptTextParser"

/**
 * Same model as [GroqChatAssistant] and [VoiceExpenseParser]. The old app's note
 * on why this is a *text* model and not a vision one is worth keeping: Groq has
 * no vision-capable model on this project's key (`meta-llama/llama-4-scout-*`
 * now 404s `model_not_found`), which is exactly why the photo is read on-device
 * first and only the resulting text is sent here.
 */
private const val GROQ_TEXT_MODEL = "openai/gpt-oss-20b"

/**
 * KMP port of the old app's `GroqReceiptExtractor.parseWithGroq` (OkHttp +
 * org.json -> Ktor + kotlinx.serialization). The prompt is carried over
 * verbatim, including the category-index trick below.
 *
 * As with the chat assistant and voice quick-add, **only the Groq tier is
 * migrated**: the old `GeminiReceiptExtractor` went through the Firebase AI
 * SDK, which is Android-only. It was already dead code there - nothing called
 * it - so nothing was lost by leaving it behind.
 *
 * Returns null only on a hard failure (network, timeout, missing key, non-2xx).
 * An unreadable photo comes back as a result with a non-OK status instead.
 */
object ReceiptTextParser {

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

    /** Throws [AiBlockedException] when the coin layer refused the call - nothing was charged. */
    suspend fun parse(
        rawText: String,
        categoryNames: List<String>,
        wallet: WalletRepository? = null
    ): ExtractedReceipt? {
        wallet?.precheck(AiFeature.Scan)?.let { if (it != AiGate.Passed) throw AiBlockedException(it) }
        val ticket = wallet?.ticket(AiFeature.Scan)
        // Groq's json_object mode (unlike Gemini's Schema.enumeration) can't enforce
        // that "category" is exactly one of the app's real category names - a free-text
        // field here would let the model paraphrase or invent a name that then silently
        // fails to match anything. Asking for an INDEX into a numbered list removes that
        // failure mode entirely: there is no string to mismatch, just an int to
        // bounds-check.
        val numberedCategories = categoryNames.mapIndexed { i, name -> "$i = $name" }.joinToString("\n")

        val prompt =
            "Below is raw OCR text read off a photo of a store receipt inside an " +
                "expense-tracking app. OCR text is often messy - misread characters, " +
                "jumbled line order, stray symbols. Use your best judgement to work out " +
                "what the receipt actually says, then respond with ONLY a JSON object - " +
                "no other text, no markdown code fences - shaped exactly like:\n" +
                "{\"status\": \"ok\"|\"blurry\"|\"not_receipt\", \"amount\": integer, " +
                "\"date\": \"yyyy-MM-dd\", \"merchant\": string, \"item\": string, " +
                "\"category_index\": integer}\n\n" +
                "- status = \"not_receipt\" if this text clearly isn't from a store " +
                "receipt or bill at all (e.g. random unrelated text).\n" +
                "- status = \"blurry\" if it looks like a receipt but the OCR text is too " +
                "garbled/incomplete to reliably read the key details.\n" +
                "- status = \"ok\" if you can make out a receipt's key details.\n\n" +
                "Only when status is \"ok\", also fill in:\n" +
                "- amount: the FINAL amount actually paid, as a whole number (no currency " +
                "symbol, no decimals). If multiple money lines appear (subtotal, tax/GST/" +
                "VAT, service charge, discount, tip, grand total), use the grand total / " +
                "amount paid / net total - never a subtotal or a tax/discount line alone. " +
                "Round to the nearest whole number.\n" +
                "- date: the purchase date, as yyyy-MM-dd. Receipts write dates in " +
                "different regional orders (day/month/year or month/day/year); use " +
                "context (e.g. a day value above 12 fixes the order) to resolve it, and if " +
                "genuinely ambiguous prefer day/month/year. Never output an impossible date.\n" +
                "- merchant: the store/business name.\n" +
                "- item: a short name for the main item(s) purchased (e.g. \"Regular Pan " +
                "Pizza\" or \"Groceries\" - keep it brief).\n" +
                "- category_index: the NUMBER (from the list below) of the single best " +
                "matching category, or -1 if nothing fits confidently. Respond with the " +
                "number only, never the category name itself:\n$numberedCategories\n\n" +
                "Leave a field out (or null) if it isn't clearly present - never guess a " +
                "value that isn't actually in the text.\n\n" +
                "OCR TEXT:\n$rawText"

        val response: HttpResponse = client.post(AiProxy.CHAT_URL) {
            ticket?.applyTo(this)
            contentType(ContentType.Application.Json)
            setBody(
                ReceiptGroqRequest(
                    model = GROQ_TEXT_MODEL,
                    temperature = 0.2,
                    responseFormat = ReceiptResponseFormat("json_object"),
                    messages = listOf(ReceiptGroqMessage(role = "user", content = prompt))
                )
            )
        }

        val bodyText = response.bodyAsText()
        val gate = wallet?.absorb(ticket, response) ?: AiGate.Passed
        if (gate != AiGate.Passed) throw AiBlockedException(gate)
        if (!response.status.isSuccess() || bodyText.isBlank()) {
            logWarn(TAG, "Groq call failed: ${response.status.value} $bodyText")
            return null
        }

        val content = json.decodeFromString<ReceiptGroqResponse>(bodyText)
            .choices.firstOrNull()?.message?.content
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val payload = json.decodeFromString<ReceiptPayload>(content)

        val status = when (payload.status?.lowercase()) {
            "blurry" -> ReceiptImageStatus.BLURRY
            "not_receipt" -> ReceiptImageStatus.NOT_A_RECEIPT
            else -> ReceiptImageStatus.OK
        }
        if (status != ReceiptImageStatus.OK) return ExtractedReceipt(status = status)

        val category = categoryNames.getOrNull(payload.categoryIndex ?: -1)
        val merchant = payload.merchant?.takeIf { it.isNotBlank() }
        val item = payload.item?.takeIf { it.isNotBlank() }
        // Merchant and item are never surfaced separately - they are fused into
        // the note field, as the old app did.
        val note = when {
            item != null && merchant != null -> "$item @ $merchant"
            item != null -> item
            else -> merchant
        }

        return ExtractedReceipt(
            amount = payload.amount?.takeIf { it > 0 }?.toString(),
            date = payload.date?.takeIf { it.isNotBlank() },
            note = note,
            category = category,
            status = ReceiptImageStatus.OK
        )
    }
}

@Serializable
private data class ReceiptGroqRequest(
    val model: String,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: ReceiptResponseFormat,
    val messages: List<ReceiptGroqMessage>
)

@Serializable
private data class ReceiptResponseFormat(val type: String)

@Serializable
private data class ReceiptGroqMessage(val role: String = "", val content: String = "")

@Serializable
private data class ReceiptGroqResponse(val choices: List<ReceiptGroqChoice> = emptyList())

@Serializable
private data class ReceiptGroqChoice(val message: ReceiptGroqMessage? = null)

/** The model's own JSON reply, parsed out of the message content string. */
@Serializable
private data class ReceiptPayload(
    val status: String? = null,
    val amount: Int? = null,
    val date: String? = null,
    val merchant: String? = null,
    val item: String? = null,
    @SerialName("category_index") val categoryIndex: Int? = null
)
