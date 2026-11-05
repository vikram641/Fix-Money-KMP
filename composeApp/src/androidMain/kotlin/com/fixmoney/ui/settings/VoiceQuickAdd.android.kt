package com.fixmoney.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.voice.FloatingBubbleService
import org.koin.compose.koinInject

/**
 * Android's half of the Settings toggle - the same flow the old app's
 * `SettingsFragment` ran, moved out of a Fragment and into a Compose handle:
 * overlay permission first, then the microphone, then start the service; turning
 * it off stops the service and clears the preference.
 *
 * Two deliberate improvements on the old flow:
 * - the old app sent you to the overlay settings screen and then made you come
 *   back and flip the switch *again*. Here the intent is launched for a result,
 *   so granting it carries straight on to the microphone step.
 * - [enabled] is recomputed from the permissions actually held, not just from the
 *   stored preference. Revoking "Display over other apps" in system settings used
 *   to leave the switch showing ON with no bubble anywhere - the old app's own
 *   code comments complain about exactly that confusion.
 */
@Composable
actual fun rememberVoiceQuickAdd(): VoiceQuickAddHandle {
    val context = LocalContext.current
    val prefs: AppPrefs = koinInject()
    val handle = remember(context) { AndroidVoiceQuickAdd(context, prefs) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        handle.onPermissionResult(granted[Manifest.permission.RECORD_AUDIO] == true)
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The overlay screen reports RESULT_CANCELED whatever the user did, so the
        // permission has to be re-read rather than trusted from the result code.
        handle.onReturnedFromOverlaySettings()
    }

    SideEffect {
        handle.requestPermissions = { permissionLauncher.launch(it) }
        handle.openOverlaySettings = { overlayLauncher.launch(it) }
    }

    // Also covers coming back to Settings after changing permissions elsewhere.
    LaunchedEffect(Unit) { handle.refresh() }

    return handle
}

private class AndroidVoiceQuickAdd(
    private val context: Context,
    private val prefs: AppPrefs
) : VoiceQuickAddHandle {

    override val supported = true

    override var enabled by mutableStateOf(false)
        private set

    override var message by mutableStateOf<String?>(null)
        private set

    /** Set while waiting for the user to come back from the overlay settings screen. */
    private var pendingEnable = false

    var requestPermissions: ((Array<String>) -> Unit)? = null
    var openOverlaySettings: ((Intent) -> Unit)? = null

    fun refresh() {
        val stored = prefs.voiceBubble()
        val live = stored && hasOverlayPermission() && hasMicPermission()
        if (stored && !live) {
            // A permission was revoked outside the app; don't keep claiming it's on.
            prefs.setVoiceBubble(false)
        }
        enabled = live
    }

    override fun toggle(on: Boolean) {
        if (!on) {
            pendingEnable = false
            prefs.setVoiceBubble(false)
            FloatingBubbleService.stop(context)
            enabled = false
            return
        }

        if (!hasOverlayPermission()) {
            pendingEnable = true
            message = "Allow \"Display over other apps\" for FixMoney to use the voice bubble"
            openOverlaySettings?.invoke(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
            enabled = false
            return
        }

        val missing = missingRuntimePermissions()
        if (missing.isNotEmpty()) {
            pendingEnable = true
            requestPermissions?.invoke(missing.toTypedArray())
            enabled = false
            return
        }

        startBubble()
    }

    override fun clearMessage() {
        message = null
    }

    fun onReturnedFromOverlaySettings() {
        if (!pendingEnable) return
        if (hasOverlayPermission()) {
            toggle(true)
        } else {
            pendingEnable = false
            message = "Without \"Display over other apps\" the bubble can't be shown"
            enabled = false
        }
    }

    fun onPermissionResult(micGranted: Boolean) {
        pendingEnable = false
        if (micGranted) {
            startBubble()
        } else {
            message = "Microphone permission is needed for voice quick-add"
            enabled = false
        }
    }

    private fun startBubble() {
        pendingEnable = false
        prefs.setVoiceBubble(true)
        FloatingBubbleService.start(context)
        enabled = true
    }

    /**
     * The microphone is required; notifications only decide whether the ongoing
     * notification is visible, but are asked for in the same prompt so the service
     * doesn't run invisibly on Android 13+.
     */
    private fun missingRuntimePermissions(): List<String> = buildList {
        if (!hasMicPermission()) add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasMicPermission() = granted(Manifest.permission.RECORD_AUDIO)

    private fun hasOverlayPermission() = Settings.canDrawOverlays(context)

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
