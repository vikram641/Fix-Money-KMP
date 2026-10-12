package com.fixmoney.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS has no overlay window - an app cannot draw over other apps at all, and
 * there is no entitlement that changes that. So the feature is reported
 * unsupported and the Settings row is hidden rather than shown doing nothing.
 *
 * If voice quick-add is ever wanted on iOS it would be an in-app mic button or a
 * Siri App Intent, both of which can call the shared `VoiceQuickAddEngine`
 * unchanged - only the bubble is Android-bound.
 */
@Composable
actual fun rememberVoiceQuickAdd(): VoiceQuickAddHandle = remember { IosVoiceQuickAdd }

private object IosVoiceQuickAdd : VoiceQuickAddHandle {
    override val supported = false
    override val enabled = false
    override val message: String? = null
    override fun toggle(on: Boolean) = Unit
    override fun clearMessage() = Unit
}
