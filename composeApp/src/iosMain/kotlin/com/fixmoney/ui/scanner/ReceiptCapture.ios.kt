package com.fixmoney.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * The system camera, presented modally.
 *
 * iOS deliberately does **not** get the Android side's custom preview with the
 * alignment frame and crop. That code can be written, but it could not be run
 * or tuned from this project at all - there is no Apple Developer account for
 * device testing and the simulator has no camera - and an untestable
 * hand-written AVFoundation camera is a worse bet than the system one, which
 * is guaranteed to work. `allowsEditing = true` gives the user a crop step,
 * which stands in for the frame: the shared pipeline only ever wanted the
 * receipt region, not a particular way of selecting it.
 *
 * `NSCameraUsageDescription` must be in `Info.plist` or the picker never
 * appears - iOS kills the app instead of showing a permission prompt.
 */
@Composable
actual fun ReceiptCaptureSurface(
    modifier: Modifier,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    // Keyed on Unit so the picker is presented once per visit to the screen,
    // not again on every recomposition.
    LaunchedEffect(Unit) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) {
            onError("Couldn't open the camera")
            return@LaunchedEffect
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.allowsEditing = true
        picker.delegate = ReceiptPickerDelegate(
            onPicked = { bytes -> onCaptured(bytes) },
            onCancelled = onCancel,
            onFailed = onError
        ).also { retainedDelegate = it }
        root.presentViewController(picker, animated = true, completion = null)
    }

    Box(modifier.background(Color.Black))
}

/**
 * `UIImagePickerController.delegate` is a weak reference, so without a strong
 * one here the delegate is collected before the user taps the shutter and the
 * callbacks never fire. Only one picker is ever on screen, so a single slot is
 * enough.
 */
private var retainedDelegate: ReceiptPickerDelegate? = null

@OptIn(ExperimentalForeignApi::class)
private class ReceiptPickerDelegate(
    private val onPicked: (ByteArray) -> Unit,
    private val onCancelled: () -> Unit,
    private val onFailed: (String) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        // The edited (cropped) image when the user cropped, else the original.
        val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage] as? UIImage)
            ?: (didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
        picker.dismissViewControllerAnimated(true, completion = null)
        retainedDelegate = null

        val bytes = image?.let { UIImageJPEGRepresentation(it, 0.9) }?.toByteArray()
        if (bytes == null) onFailed("Couldn't read that photo, try again") else onPicked(bytes)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        retainedDelegate = null
        onCancelled()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }
}

/** Skia is already in the binary for Compose, so no extra decoder is needed. */
actual fun decodeReceiptPreview(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
