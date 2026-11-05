package com.fixmoney.shared.receipt

import com.fixmoney.shared.coins.AiBlockedException
import com.fixmoney.shared.coins.WalletRepository
import com.fixmoney.shared.ai.ReceiptTextParser
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.platform.logWarn

private const val TAG = "ReceiptScanEngine"

/**
 * The whole scan minus the camera: photo bytes in, [ExtractedReceipt] out.
 *
 * Port of the old `GroqReceiptExtractor.extract`, and shared for the same
 * reason [com.fixmoney.shared.voice.VoiceQuickAddEngine] is: only the capture
 * is platform-bound, and both platforms want identical behaviour from the photo
 * onwards.
 *
 * Two stages rather than one multimodal call:
 *  1. on-device OCR ([recognizeReceiptText]) - instant, free, nothing leaves
 *     the phone,
 *  2. Groq's text model turns that messy text into fields.
 *
 * Unlike the old extractor this fetches the category list itself instead of
 * having it passed down from the screen. The old app's Add Expense had to pass
 * a `String[]` through a Bundle and could enable the scan button before
 * categories had loaded, which produced an empty numbered list and silently
 * broke category autofill; asking the repository here cannot get out of sync.
 */
class ReceiptScanEngine(
    private val categories: CategoryRepository,
    private val wallet: WalletRepository
) {

    /** Throws [AiBlockedException] when AI coins refused the scan - nothing was charged. */
    suspend fun scan(imageBytes: ByteArray): ExtractedReceipt? {
        val rawText = try {
            recognizeReceiptText(imageBytes)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e // leaving the screen must cancel the call, not report a failure
        } catch (e: Exception) {
            logWarn(TAG, "OCR failed: ${e.message}")
            return null
        }

        // Too little text recognized to be a readable receipt - almost always a
        // blurry/dark/out-of-focus shot rather than a genuinely blank one, since
        // the user already framed it in the scan box.
        if (rawText.trim().length < 10) return ExtractedReceipt(status = ReceiptImageStatus.BLURRY)

        val categoryNames = try {
            categories.getCategories().map { it.name }
        } catch (e: Exception) {
            logWarn(TAG, "categories unavailable: ${e.message}")
            emptyList()
        }

        return try {
            ReceiptTextParser.parse(rawText, categoryNames, wallet)
        } catch (e: AiBlockedException) {
            throw e
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e // leaving the screen must cancel the call, not report a failure
        } catch (e: Exception) {
            logWarn(TAG, "parse failed: ${e.message}")
            null
        }
    }
}
