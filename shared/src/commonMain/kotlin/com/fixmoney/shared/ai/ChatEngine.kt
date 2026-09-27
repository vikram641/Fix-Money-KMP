package com.fixmoney.shared.ai

import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.onboarding.OnboardingPrefs

/**
 * Builds the DATA summary the old `ChatViewModel` assembled from Room, then
 * asks [GroqChatAssistant]. The old app tried Gemini first and fell back to
 * Groq; Gemini used the Firebase AI SDK, which is Android-only, so only the
 * Groq tier is migrated (the plan's section 6.2 has the REST route for Gemini
 * when it's needed).
 */
class ChatEngine(
    private val expenses: ExpenseDao,
    private val prefs: OnboardingPrefs
) {

    suspend fun ask(question: String, history: List<Pair<String, String>>): ChatAskResult {
        val rows = expenses.getExpensesOnce()

        val summary = buildString {
            appendLine("Monthly budget: ${prefs.getMonthlyBudget()}")
            appendLine("Currency: ${prefs.getCurrencyCode()}")
            if (rows.isEmpty()) {
                appendLine("No expenses recorded yet.")
            } else {
                appendLine("Total spent: ${rows.sumOf { it.amount.toIntOrNull() ?: 0 }}")
                appendLine("By category:")
                rows.groupBy { it.categoryName }.forEach { (name, group) ->
                    appendLine("  $name: ${group.sumOf { it.amount.toIntOrNull() ?: 0 }}")
                }
                appendLine("Recent:")
                rows.take(15).forEach {
                    appendLine("  ${it.date} ${it.categoryName} ${it.amount} ${it.note}")
                }
            }
        }

        val historyText = history.joinToString("\n") { (q, a) -> "Q: $q\nA: $a" }
        return GroqChatAssistant.ask(Secrets.GROQ_API_KEY, question, summary, historyText)
    }
}
