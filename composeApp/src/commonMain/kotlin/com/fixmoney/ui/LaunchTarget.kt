package com.fixmoney.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A screen or sheet something outside the app asked to open - today only the
 * voice bubble. When it is refused for coins it opens the out-of-coins sheet
 * (video +5 and Fixoo Premium), not the wallet: the user asked for the way to
 * carry on, and the sheet is that, one tap away.
 *
 * Held here rather than read from the Android intent inside `App`, so the
 * navigation stays in common code; `MainActivity` only writes it. `App` takes
 * it once the user is past splash and onboarding, and clears it, so a
 * recomposition or a rotation never opens the screen twice.
 */
object LaunchTarget {
    const val EXTRA = "open"
    const val WALLET = "wallet"
    /** The out-of-coins sheet: watch a video, or get Fixoo Premium. */
    const val OUT_OF_COINS = "out_of_coins"
    /** The trial's daily AI limit sheet, which also offers Fixoo Premium. */
    const val TRIAL_LIMIT = "trial_limit"
    private val KNOWN = setOf(WALLET, OUT_OF_COINS, TRIAL_LIMIT)

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(target: String?) {
        if (target in KNOWN) _pending.value = target
    }

    fun consume() {
        _pending.value = null
    }
}
