package com.fixmoney

import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.coins.shortMessage
import com.fixmoney.shared.voice.VoiceAddResult
import com.fixmoney.shared.voice.VoiceQuickAddEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** What Siri should say back, and whether anything was actually saved. */
data class VoiceAddOutcome(val saved: Boolean, val message: String)

/**
 * What the Android floating bubble is on iOS.
 *
 * iOS has no overlay window - no third-party app can draw over another one, with
 * or without an entitlement - so the bubble itself cannot be ported. A Siri App
 * Intent gets to the same place by a different door: it works with the app closed,
 * from anywhere in the system, which is the part of the bubble that actually
 * mattered.
 *
 * Siri does the speech-to-text, so there is no `SFSpeechRecognizer` here and no
 * microphone permission to ask for - the intent hands over an already-transcribed
 * sentence. Everything after that is `VoiceQuickAddEngine`, the same shared code
 * the Android service calls.
 *
 * Exposed to Swift as `VoiceQuickAddBridge.shared`. Deliberately callback-based
 * rather than `suspend`: an exported suspend function is awkward to call from a
 * `perform()` that is already `async`, and this keeps the threading explicit.
 */
object VoiceQuickAddBridge : KoinComponent {

    // Lazy on purpose: first touched inside the coroutine below, i.e. after
    // ensureKoinStarted(), which matters when a Siri intent is what launched the
    // process.
    private val engine: VoiceQuickAddEngine by inject()

    fun addFromSpeech(spokenText: String, onResult: (VoiceAddOutcome) -> Unit) {
        // Main queue on purpose: the intent can run on a background thread, and
        // ensureKoinStarted() assumes one starter at a time.
        CoroutineScope(Dispatchers.Main).launch {
            ensureKoinStarted()

            val outcome = when (val result = engine.addFromSpeech(spokenText)) {
                is VoiceAddResult.Saved -> VoiceAddOutcome(
                    saved = true,
                    // Spoken back by Siri, so this is a sentence rather than the
                    // bubble's "✓ ₹150 • Food" toast.
                    message = "Added ${CurrencyConstants.symbolFor(result.currency)}" +
                        "${result.amount} to ${result.categoryName}."
                )
                is VoiceAddResult.NoAmount -> VoiceAddOutcome(
                    saved = false,
                    message = "I heard \"${result.spokenText}\" but no amount, so nothing was saved."
                )
                is VoiceAddResult.Failed -> VoiceAddOutcome(
                    saved = false,
                    message = "I couldn't add that: ${result.reason}"
                )
                // Siri has no screen to show a video on, so it says what happened
                // and the user tops up from the app.
                is VoiceAddResult.Blocked -> VoiceAddOutcome(saved = false, message = result.gate.shortMessage())
            }

            onResult(outcome)
        }
    }
}
