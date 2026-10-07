package com.fixmoney.ui.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmoney.shared.receipt.ExtractedReceipt
import com.fixmoney.shared.receipt.ReceiptImageStatus
import com.fixmoney.shared.receipt.ReceiptScanEngine
import com.fixmoney.shared.coins.AiBlockedException
import com.fixmoney.shared.coins.shortMessage
import com.fixmoney.ui.coins.LocalCoins
import com.fixmoney.resources.Res
import com.fixmoney.resources.nd_check_circle
import com.fixmoney.resources.nd_scan_doc
import com.fixmoney.resources.nd_sparkle
import com.fixmoney.ui.Design
import com.fixmoney.ui.components.LineIcon
import com.fixmoney.ui.designType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Port of `ReceiptScannerFragment` - the capture -> review -> scan flow, with
 * only the capture step delegated to the platform ([ReceiptCaptureSurface]).
 *
 * The review step is deliberate and carried over from the old app: OCR and the
 * Groq call fire only after the user looks at the photo and taps Scan Receipt,
 * so a shot they would have retaken anyway never costs an API call.
 *
 * Every failure is recoverable in place - the message is shown and Retake /
 * Scan Receipt come back, rather than dropping the user out of the screen.
 *
 * Styled to the 2026 mockup (`fixmoney_scan_receipt_viewfinder_obsidian_theme`):
 * corner brackets on the viewfinder, a status strip over the shutter, and a
 * round shutter button.
 *
 * **Four things in that mockup are not here**, all of them claims or controls
 * with nothing behind them:
 *
 * - **"AI AUTO-DETECT • 99.4%"**. There is no live detection and no confidence
 *   score; the OCR and the Groq call run *after* the shutter, on the still.
 *   The chip says "AI receipt scan", which is what it is.
 * - **"Targeting Merchant, GSTIN & Tax"**. The parser extracts amount, date,
 *   note and category. It does not read a GSTIN.
 * - **Flash**. CameraX can do it, but the capture surface does not expose a
 *   torch control yet, and iOS uses the system camera which has its own.
 * - **Gallery**. There is no photo picker in this build - that is the same
 *   missing piece "Attach Photo" always was.
 */
@Composable
fun ReceiptScannerScreen(
    onClose: () -> Unit,
    onScanned: (ExtractedReceipt) -> Unit
) {
    val colors = Design.colors
    val t = designType()
    val engine: ReceiptScanEngine = koinInject()
    val coins = LocalCoins.current
    val scope = rememberCoroutineScope()

    var captured by remember { mutableStateOf<ByteArray?>(null) }
    var reading by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Align receipt within frame") }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val shot = captured
        if (shot == null) {
            ReceiptCaptureSurface(
                modifier = Modifier.fillMaxSize(),
                onCaptured = { bytes ->
                    captured = bytes
                    hint = "Looks good?"
                },
                onCancel = onClose,
                onError = { hint = it }
            )
        } else {
            val preview = remember(shot) { decodeReceiptPreview(shot) }
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = "Captured receipt",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopCenter)
                .fillMaxWidth().statusBarsPadding().padding(16.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 19.sp, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, colors.accent.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                LineIcon(Res.drawable.nd_sparkle, colors.accent, 14.dp)
                Spacer(Modifier.width(7.dp))
                Text("AI RECEIPT SCAN", style = t.labelCaps, color = colors.accent)
            }
            Spacer(Modifier.weight(1f))
            // Balances the row so the chip sits centred, without pretending to
            // be the mockup's flash button.
            Spacer(Modifier.size(44.dp))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (reading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent
                        )
                    } else {
                        LineIcon(Res.drawable.nd_scan_doc, Color.White, 19.dp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(hint, style = t.titleMd, color = Color.White, maxLines = 2)
                    Text(
                        "Amount, date and category are read from the photo",
                        style = t.bodySm,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // No shutter here: `ReceiptCaptureSurface` owns its capture control,
            // because the two platforms take different shapes (CameraX preview
            // with a cut-out on Android, the system camera on iOS). This column
            // only has to leave room below the status strip for it.
            if (shot == null && !reading) {
                Spacer(Modifier.height(110.dp))
            }

            if (shot != null && !reading) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ScannerButton(
                        label = "Retake",
                        filled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        captured = null
                        hint = "Align receipt within frame"
                    }
                    ScannerButton(
                        label = "Scan Receipt",
                        filled = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        reading = true
                        hint = "Reading receipt…"
                        scope.launch {
                            val result = try {
                                engine.scan(shot)
                            } catch (e: AiBlockedException) {
                                // AI coins refused the scan; nothing was charged.
                                reading = false
                                hint = e.gate.shortMessage()
                                coins?.onBlocked(e.gate)
                                return@launch
                            }
                            reading = false
                            val retry = retryMessageFor(result)
                            if (retry != null) hint = retry else onScanned(result!!)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The old `onExtractionFinished`'s message ladder, unchanged. Null means the
 * result is good enough to hand back - note that a *partial* read (say an
 * amount but no date) still counts as success, as it did before.
 */
private fun retryMessageFor(result: ExtractedReceipt?): String? = when {
    result == null -> "Couldn't read receipt — check your connection and try again"
    result.status == ReceiptImageStatus.BLURRY ->
        "Image is too blurry to read — hold steady and try again"
    result.status == ReceiptImageStatus.NOT_A_RECEIPT ->
        "That doesn't look like a receipt — try scanning a bill or receipt"
    result.amount == null && result.date == null && result.note == null ->
        "Couldn't find any details on this receipt, try again"
    else -> null
}

@Composable
private fun ScannerButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = Design.colors
    Box(
        modifier = modifier.clip(CircleShape)
            .background(if (filled) colors.accent else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
            color = Color.White
        )
    }
}
