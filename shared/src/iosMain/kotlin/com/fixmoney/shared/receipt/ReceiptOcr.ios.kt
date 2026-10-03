package com.fixmoney.shared.receipt

import com.fixmoney.shared.platform.logWarn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import kotlin.coroutines.resume

private const val TAG = "ReceiptOcr"

/**
 * Apple's Vision framework - the iOS counterpart to ML Kit. Also on-device,
 * also free, and it ships with the OS, so unlike every other library in this
 * project it has no `iosX64` / Kotlin-version klib gate to clear.
 *
 * `recognitionLevel = accurate` and `usesLanguageCorrection = true` match what
 * ML Kit does by default. Vision hands back one observation per text line, so
 * the lines are joined with newlines to give the parser the same shape of input
 * ML Kit's `Text.text` produces.
 *
 * Deliberately **not** run on a background dispatcher: `performRequests` is
 * synchronous but Vision schedules its own work, and the completion handler
 * resumes the coroutine.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun recognizeReceiptText(imageBytes: ByteArray): String =
    suspendCancellableCoroutine { cont ->
        val data: NSData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val cgImage = UIImage.imageWithData(data)?.CGImage
        if (cgImage == null) {
            logWarn(TAG, "could not decode ${imageBytes.size} bytes of image")
            cont.resume("")
            return@suspendCancellableCoroutine
        }

        val request = VNRecognizeTextRequest { request, error ->
            if (error != null) {
                logWarn(TAG, "Vision failed: ${error.localizedDescription}")
                cont.resume("")
                return@VNRecognizeTextRequest
            }
            val lines = request?.results.orEmpty().mapNotNull { result ->
                (result as? VNRecognizedTextObservation)
                    ?.topCandidates(1u)
                    ?.firstOrNull()
                    ?.let { candidate ->
                        @Suppress("UNCHECKED_CAST")
                        (candidate as platform.Vision.VNRecognizedText).string
                    }
            }
            cont.resume(lines.joinToString("\n"))
        }
        request.setRecognitionLevel(VNRequestTextRecognitionLevelAccurate)
        request.setUsesLanguageCorrection(true)

        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
        try {
            handler.performRequests(listOf(request), null)
        } catch (e: Exception) {
            logWarn(TAG, "Vision threw: ${e.message}")
            if (cont.isActive) cont.resume("")
        }
    }
