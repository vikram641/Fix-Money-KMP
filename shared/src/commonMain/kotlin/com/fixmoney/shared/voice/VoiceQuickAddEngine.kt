package com.fixmoney.shared.voice

import com.fixmoney.shared.coins.AiBlockedException
import com.fixmoney.shared.coins.AiGate
import com.fixmoney.shared.coins.WalletRepository
import com.fixmoney.shared.ai.VoiceExpenseParser
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.expense.ExpenseRepository
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.logWarn
import com.fixmoney.shared.platform.todayIso

private const val TAG = "VoiceQuickAddEngine"

/** What a single "tap the bubble and speak" attempt ended in. */
sealed interface VoiceAddResult {
    /** Saved. [categoryName] is what the row was filed under. */
    data class Saved(val amount: Int, val categoryName: String, val currency: String) : VoiceAddResult

    /** Understood the speech but found no money in it - the old app's "no amount" toast. */
    data class NoAmount(val spokenText: String) : VoiceAddResult

    /** Nothing could be parsed - network, quota, or a malformed reply. */
    data class Failed(val reason: String) : VoiceAddResult

    /** AI coins refused the call (no coins, trial cap, or AI busy). Nothing was charged or saved. */
    data class Blocked(val gate: AiGate) : VoiceAddResult
}

/**
 * The whole voice quick-add flow minus the microphone: parse one spoken
 * sentence, match it to a real category, and write the expense to Room.
 *
 * Deliberately shared rather than living in the Android service, unlike the old
 * app where all of this sat inside `FloatingBubbleService`. The floating bubble
 * itself is Android-only (an overlay window has no iOS equivalent), but nothing
 * here is - if iOS ever gets a Siri shortcut or an in-app mic button, it calls
 * this same object.
 */
class VoiceQuickAddEngine(
    private val categories: CategoryRepository,
    private val expenses: ExpenseRepository,
    private val prefs: OnboardingPrefs,
    private val wallet: WalletRepository
) {

    suspend fun addFromSpeech(spokenText: String): VoiceAddResult {
        val available = categories.getCategories()
        val names = available.map { it.name }

        // The easy phrases are read on the phone: free, offline, instant. Only
        // what LocalVoiceParser is unsure about reaches the model (and costs a coin).
        val local = LocalVoiceParser.parse(spokenText, names)
        if (local == LocalVoiceParser.Result.NoAmount) return VoiceAddResult.NoAmount(spokenText)

        val parsed = if (local is LocalVoiceParser.Result.Parsed) local.expense else try {
            VoiceExpenseParser.parse(spokenText, names, wallet)
        } catch (e: AiBlockedException) {
            return VoiceAddResult.Blocked(e.gate)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarn(TAG, "voice parse failed: ${e.message}")
            // The raw text ("Groq call failed: 429", "Timed out waiting for 20000 ms")
            // used to reach the toast verbatim.
            val msg = e.message.orEmpty()
            return VoiceAddResult.Failed(
                when {
                    "429" in msg -> "too many requests right now, try again in a minute"
                    "imed out" in msg || "timeout" in msg.lowercase() -> "that took too long, please try again"
                    else -> "couldn't understand that right now"
                }
            )
        }

        if (parsed.amount <= 0) return VoiceAddResult.NoAmount(spokenText)

        // The old app saved an *empty* categoryId when nothing matched and only
        // said "Other" in the toast, which left the row out of every by-category
        // roll-up. Falling back to the real "Other" category keeps the donut,
        // Analytics and Budget totals correct.
        val category = parsed.category
            ?.let { name -> available.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            ?: available.firstOrNull { it.name.equals("Other", ignoreCase = true) }
            ?: return VoiceAddResult.Failed("no categories available")

        val currency = prefs.getCurrencyCode()

        expenses.addExpense(
            amount = parsed.amount,
            // The old app hardcoded "INR" here even though onboarding asks for a
            // currency; this respects the saved one, like Add Expense does.
            currency = currency,
            dateIso = parsed.date ?: todayIso(),
            note = parsed.note.ifBlank { "Voice quick add" },
            category = category
        )

        return VoiceAddResult.Saved(parsed.amount, category.name, currency)
    }
}
