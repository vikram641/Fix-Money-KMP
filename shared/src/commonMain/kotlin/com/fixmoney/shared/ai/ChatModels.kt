package com.fixmoney.shared.ai

/**
 * Shared-module copies of the chat result types that currently live in
 * `com.example.expense.feature.chat.GeminiChatAssistant`. They stay duplicated
 * for now, with the Android `GroqChatAssistant` mapping between them, so this
 * first KMP step touches nothing outside the chat package. When Gemini moves
 * to a plain Ktor call too (section 6.2 of the migration plan), the Android
 * copies get deleted and these become the only definitions.
 */
data class ChatCategoryAmount(val name: String, val amount: Int)

data class ChatAnswer(val text: String, val categories: List<ChatCategoryAmount> = emptyList())

/** Distinguishes "the free-tier rate limit was hit" from any other failure, so the UI
 * can tell the user something accurate instead of a generic "couldn't reach" message. */
sealed class ChatAskResult {
    data class Success(val answer: ChatAnswer) : ChatAskResult()
    object QuotaExceeded : ChatAskResult()
    object Failure : ChatAskResult()
    /**
     * The coin layer refused the call - no coins, a daily limit (trial or
     * premium), or every AI provider busy. Nothing was charged.
     */
    data class Blocked(val gate: com.fixmoney.shared.coins.AiGate) : ChatAskResult()
}
