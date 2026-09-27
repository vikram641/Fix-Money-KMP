package com.fixmoney.shared.onboarding

import com.russhwolf.settings.Settings

/**
 * The Settings screen's toggles. The old app kept these in a separate
 * `"settings"` SharedPreferences file (`dark_mode`, etc.) rather than in the
 * onboarding one - same split here.
 */
class AppPrefs(private val settings: Settings) {

    /** null = follow the system, true/false = explicit override. */
    fun darkMode(): Boolean? =
        if (settings.hasKey(KEY_DARK)) settings.getBoolean(KEY_DARK, false) else null

    fun setDarkMode(value: Boolean) = settings.putBoolean(KEY_DARK, value)

    fun budgetAlerts(): Boolean = settings.getBoolean(KEY_ALERTS, true)
    fun setBudgetAlerts(value: Boolean) = settings.putBoolean(KEY_ALERTS, value)

    fun voiceBubble(): Boolean = settings.getBoolean(KEY_VOICE, false)
    fun setVoiceBubble(value: Boolean) = settings.putBoolean(KEY_VOICE, value)

    private companion object {
        const val KEY_DARK = "dark_mode"
        const val KEY_ALERTS = "budget_alerts"
        const val KEY_VOICE = "voice_bubble"
    }
}
