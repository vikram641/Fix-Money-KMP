package com.fixmoney.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

/** The cut-out, as fractions of the surface - 85% x 55%, centred, same as the old overlay view. */
private const val FRAME_WIDTH_FRACTION = 0.85f
private const val FRAME_HEIGHT_FRACTION = 0.55f

/**
 * CameraX preview with the old app's alignment overlay, rebuilt in Compose.
 *
 * The frame is not decoration: [cropToFrame] maps it back onto the captured
 * photo so only what the user framed reaches OCR. Sending the whole photo -
 * receipt plus table, hands and background - measurably hurts extraction.
 */
@Composable
actual fun ReceiptCaptureSurface(
    modifier: Modifier,
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasCameraHardware = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // The old app bounced the user out of the scanner on denial with a toast and
    // no way back in. Staying put with an explanation is strictly better: the
    // system dialog can be reached again by retrying, and a permanent denial at
    // least leaves the message on screen instead of a vanished toast.
    var denied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        denied = !result
    }

    LaunchedEffect(hasCameraHardware) {
        if (!hasCameraHardware) {
            onError("No camera available on this device")
        } else if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraHardware || !granted) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            if (denied) {
                androidx.compose.material3.Text(
                    "Camera access is needed to scan a receipt.\nTap to allow.",
                    color = Color.White,
                    modifier = Modifier
                        .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        .padding(24.dp)
                )
            }
        }
        return
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Resolved asynchronously. The obvious `getInstance(ctx).get()` blocks the
    // main thread, and doing it from `AndroidView`'s onRelease was worse still:
    // `unbindAll()` is global, so a *previous* surface being released after the
    // next one had bound tore the live preview down again - which is exactly
    // what happened, the camera ran for 12s and then closed itself.
    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cameraProvider = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(cameraProvider, previewView) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            imageCapture = capture
        } catch (e: Exception) {
            onError("Unable to start camera")
        }
    }

    // The camera is bound to the Activity's lifecycle, which outlives this
    // screen, so leaving it has to unbind explicitly or the camera stays open.
    DisposableEffect(cameraProvider) {
        onDispose { cameraProvider?.unbindAll() }
    }
    // Read on the main thread at capture time: the CameraX callback runs on a
    // background executor and must not touch the composition.
    var surfaceSize by remember { mutableStateOf(IntSizePx(0, 0)) }

    // Measured on the layout pass, not written from inside the draw scope:
    // setting state while drawing schedules another draw to read it back.
    Box(modifier.onSizeChanged { surfaceSize = IntSizePx(it.width, it.height) }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            }
        )

        // Dim + cut-out + corner brackets + the sweeping scan line.
        val transition = rememberInfiniteTransition(label = "scanline")
        val sweep by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sweep"
        )

        Canvas(
            Modifier.fillMaxSize()
                // BlendMode.Clear only punches a hole when the layer is drawn
                // offscreen first; without this it paints black instead.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val frameWidth = size.width * FRAME_WIDTH_FRACTION
            val frameHeight = size.height * FRAME_HEIGHT_FRACTION
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f
            val radius = CornerRadius(18.dp.toPx(), 18.dp.toPx())

            drawRect(Color.Black.copy(alpha = 0.6f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = radius,
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            val lineY = top + frameHeight * sweep
            drawLine(
                color = Color(0xFF7B61FF),
                start = Offset(left + 8.dp.toPx(), lineY),
                end = Offset(left + frameWidth - 8.dp.toPx(), lineY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Corner brackets on the cut-out, as the mockup has. Drawn here rather
        // than in the shared screen because this is where the frame's geometry
        // is known - the shared side has no idea where the cut-out landed.
        Canvas(Modifier.fillMaxSize()) {
            // The same geometry the cut-out uses, so the brackets land exactly
            // on its corners rather than near them.
            val frameWidth = size.width * FRAME_WIDTH_FRACTION
            val frameHeight = size.height * FRAME_HEIGHT_FRACTION
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f
            val arm = 26.dp.toPx()
            val stroke = 3.dp.toPx()
            val accent = Color(0xFF9B84FF)
            listOf(
                // (x, y, dx, dy) for each corner's two arms
                Triple(Offset(left, top), Offset(arm, 0f), Offset(0f, arm)),
                Triple(Offset(left + frameWidth, top), Offset(-arm, 0f), Offset(0f, arm)),
                Triple(Offset(left, top + frameHeight), Offset(arm, 0f), Offset(0f, -arm)),
                Triple(
                    Offset(left + frameWidth, top + frameHeight),
                    Offset(-arm, 0f),
                    Offset(0f, -arm)
                )
            ).forEach { (corner, horizontal, vertical) ->
                drawLine(
                    accent, corner,
                    Offset(corner.x + horizontal.x, corner.y + horizontal.y),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
                drawLine(
                    accent, corner,
                    Offset(corner.x + vertical.x, corner.y + vertical.y),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
            }
        }

        // Shutter - the mockup's ring-around-a-disc rather than a plain circle.
        // The ring is a separate Box so the disc can shrink while pressed
        // without the outline moving with it.
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(bottom = 96.dp)
                .size(78.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(2.dp, Color.White.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
        Box(
            modifier = Modifier
                .size(if (capturing) 52.dp else 60.dp).clip(CircleShape)
                .background(if (capturing) Color(0xFFBDB4E8) else Color.White)
                .clickable(enabled = !capturing && imageCapture != null) {
                    val capture = imageCapture ?: return@clickable
                    capturing = true
                    val width = surfaceSize.width
                    val height = surfaceSize.height
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bytes = try {
                                    image.toUprightBitmap()
                                        ?.let { cropToFrame(it, width, height) }
                                        ?.toJpegBytes()
                                } finally {
                                    image.close()
                                }
                                capturing = false
                                if (bytes == null) onError("Couldn't read that photo, try again")
                                else onCaptured(bytes)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                onError("Capture failed, try again")
                            }
                        }
                    )
                }
        )
        }
    }
}

private data class IntSizePx(val width: Int, val height: Int)

/** JPEG planes, decoded and rotated upright so OCR never sees a sideways receipt. */
private fun ImageProxy.toUprightBitmap(): Bitmap? {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

/**
 * Maps the on-screen frame back onto the full-resolution photo and crops to it.
 *
 * `PreviewView`'s FILL_CENTER scale type centre-crops the camera stream to fill
 * the surface, so the frame covers only part of what the capture contains - the
 * two aspect ratios are all that is needed to undo that. 8% padding because the
 * frame is a guide, not a hard boundary. Ported unchanged from the old app.
 */
private fun cropToFrame(bitmap: Bitmap, viewWidth: Int, viewHeight: Int): Bitmap {
    if (viewWidth <= 0 || viewHeight <= 0) return bitmap

    val frameWidth = viewWidth * FRAME_WIDTH_FRACTION
    val frameHeight = viewHeight * FRAME_HEIGHT_FRACTION
    val frameLeft = (viewWidth - frameWidth) / 2f
    val frameTop = (viewHeight - frameHeight) / 2f

    val viewAspect = viewWidth.toFloat() / viewHeight
    val bitmapAspect = bitmap.width.toFloat() / bitmap.height

    val visibleLeftFraction: Float
    val visibleTopFraction: Float
    val visibleWidthFraction: Float
    val visibleHeightFraction: Float
    if (bitmapAspect >= viewAspect) {
        visibleWidthFraction = viewAspect / bitmapAspect
        visibleLeftFraction = (1f - visibleWidthFraction) / 2f
        visibleHeightFraction = 1f
        visibleTopFraction = 0f
    } else {
        visibleHeightFraction = bitmapAspect / viewAspect
        visibleTopFraction = (1f - visibleHeightFraction) / 2f
        visibleWidthFraction = 1f
        visibleLeftFraction = 0f
    }

    fun mapX(viewX: Float) = visibleLeftFraction + (viewX / viewWidth) * visibleWidthFraction
    fun mapY(viewY: Float) = visibleTopFraction + (viewY / viewHeight) * visibleHeightFraction

    val padX = frameWidth * 0.08f
    val padY = frameHeight * 0.08f

    val left = (mapX(frameLeft - padX).coerceIn(0f, 1f) * bitmap.width).toInt()
    val top = (mapY(frameTop - padY).coerceIn(0f, 1f) * bitmap.height).toInt()
    val right = (mapX(frameLeft + frameWidth + padX).coerceIn(0f, 1f) * bitmap.width).toInt()
    val bottom = (mapY(frameTop + frameHeight + padY).coerceIn(0f, 1f) * bitmap.height).toInt()

    val cropWidth = right - left
    val cropHeight = bottom - top
    if (cropWidth <= 0 || cropHeight <= 0) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}

/** 90 is plenty for OCR and keeps the in-memory handoff small. */
private fun Bitmap.toJpegBytes(): ByteArray = ByteArrayOutputStream().use { out ->
    compress(Bitmap.CompressFormat.JPEG, 90, out)
    out.toByteArray()
}

actual fun decodeReceiptPreview(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
