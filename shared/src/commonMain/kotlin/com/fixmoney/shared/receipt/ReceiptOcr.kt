package com.fixmoney.shared.receipt

/**
 * Reads whatever text is in an encoded photo (JPEG/PNG bytes), on-device.
 *
 * Bytes rather than a platform image type on purpose: it keeps
 * [ReceiptScanEngine] and the whole shared pipeline free of `expect class`,
 * and the camera layer has to encode the frame anyway.
 *
 * Android uses ML Kit Text Recognition v2; iOS uses the Vision framework. Both
 * are free, offline and bundled - no OCR traffic leaves the device. Only the
 * resulting *text* is sent anywhere (to Groq, by [ReceiptTextParser]).
 *
 * Returns "" when nothing could be read; it never throws for an unreadable
 * photo, only for a genuinely broken one.
 */
expect suspend fun recognizeReceiptText(imageBytes: ByteArray): String
