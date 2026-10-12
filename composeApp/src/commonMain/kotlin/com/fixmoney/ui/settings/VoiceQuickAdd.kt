package com.fixmoney.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * The Settings screen's "Floating Voice Quick-Add" toggle, behind a handle so the
 * screen itself stays in common code.
 *
 * Everything this controls is Android-only - an always-on overlay window has no
 * iOS equivalent, and no third-party app can draw over another one there at all.
 * Rather than showing a toggle that can never do anything, [supported] is false
 * on iOS and the row is hidden. The shared half of the feature (parse the speech,
 * match a category, save the expense) is `VoiceQuickAddEngine` in the shared
 * module and is not platform-bound.
 */
@Stable
interface VoiceQuickAddHandle {

    /** False on iOS: the row is hidden entirely rather than shown inert. */
    val supported: Boolean

    /** Reflects reality, not just the stored preference - see the Android actual. */
    val enabled: Boolean

    /** One-shot text to show the user (a permission is missing, etc.). */
    val message: String?

    /**
     * Named `toggle`, not `setEnabled`: on the JVM that would collide with the
     * setter the [enabled] property already generates (`setEnabled(Z)V`).
     */
    fun toggle(on: Boolean)

    fun clearMessage()
}

/**
 * Wires the toggle to its platform. On Android this owns the permission dance
 * (overlay, then microphone) and starts/stops the bubble service; on iOS it is
 * an inert stub.
 */
@Composable
expect fun rememberVoiceQuickAdd(): VoiceQuickAddHandle
