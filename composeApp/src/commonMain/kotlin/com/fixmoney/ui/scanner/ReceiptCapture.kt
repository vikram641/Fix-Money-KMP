package com.fixmoney.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The one platform-bound half of the receipt scanner: getting a photo.
 * Everything after the shutter - review, OCR, parsing, applying the fields - is
 * shared.
 *
 * The surface owns its own capture control, rather than the shared screen
 * supplying a button, because the two platforms take genuinely different
 * shapes here:
 *
 * - **Android** rebuilds the old app's scanner: a live CameraX preview with a
 *   dimmed overlay, a cut-out alignment frame and a shutter button, and it
 *   crops the captured frame to that cut-out before handing the bytes back.
 *   Cropping is not cosmetic - it is what keeps the surrounding table, hands
 *   and background out of the OCR text.
 * - **iOS** presents the system camera (`UIImagePickerController`) and renders
 *   nothing of its own. A hand-written AVFoundation preview with a matching
 *   overlay is possible, but it could not be run or tuned at all from here -
 *   this project has no Apple Developer account for device testing and the
 *   simulator has no camera - and an untestable custom camera is worse than the
 *   system one, which definitely works. `allowsEditing` gives the user a crop
 *   step, which stands in for the frame.
 *
 * [onCaptured] receives **encoded** image bytes (JPEG), already rotated
 * upright. Bytes rather than a platform bitmap keep the rest of the pipeline,
 * including [com.fixmoney.shared.receipt.recognizeReceiptText], in common code.
 */
@Composable
expect fun ReceiptCaptureSurface(
    modifier: Modifier,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
)

/** Decodes the captured JPEG for the review step. */
expect fun decodeReceiptPreview(bytes: ByteArray): ImageBitmap?
