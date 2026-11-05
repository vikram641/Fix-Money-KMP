package com.fixmoney.shared.receipt

import android.graphics.BitmapFactory
import com.fixmoney.shared.platform.logWarn
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "ReceiptOcr"

/** Held statically, as the old `GroqReceiptExtractor` did - building a recognizer per scan reloads the model. */
private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

/**
 * ML Kit Text Recognition v2, Latin script, bundled on-device model - the same
 * library and the same 8s budget the old app used.
 *
 * The rotation argument to `InputImage.fromBitmap` is 0 because the camera
 * layer already rotated the frame upright before encoding it (see
 * `ReceiptCapture.android.kt`), exactly as the old fragment did in
 * `imageProxyToBitmap`.
 */
actual suspend fun recognizeReceiptText(imageBytes: ByteArray): String = withContext(Dispatchers.Default) {
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    if (bitmap == null) {
        logWarn(TAG, "could not decode ${imageBytes.size} bytes of image")
        return@withContext ""
    }
    withTimeout(8_000) { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text }
}
