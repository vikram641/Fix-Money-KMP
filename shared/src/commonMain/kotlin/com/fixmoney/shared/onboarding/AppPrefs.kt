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

    /**
     * The 2026 Settings design's switches, **stored but not yet acted on**.
     *
     * Each one names a feature that does not exist in this build (an autonomous
     * spend guard, merchant auto-tagging, sweeping unspent budget, a biometric
     * lock, a blur compositor, haptics). The preference is persisted so the
     * screen remembers what the user chose and nothing is lost when the feature
     * lands; **no code reads these back yet**. Wire each one as its feature is
     * built, and drop the "Preview" pill from its row at the same time.
     */
    fun preview(key: PreviewToggle): Boolean = settings.getBoolean(key.key, key.default)
    fun setPreview(key: PreviewToggle, value: Boolean) = settings.putBoolean(key.key, value)

    /** The month day a budget cycle starts on. Stored; nothing reads it yet. */
    fun fiscalCycleStart(): Int = settings.getInt(KEY_CYCLE_START, 1)
    fun setFiscalCycleStart(day: Int) = settings.putInt(KEY_CYCLE_START, day.coerceIn(1, 28))

    /** How often Fixoo should brief the user. Stored; nothing reads it yet. */
    fun insightFrequency(): String = settings.getStringOrNull(KEY_INSIGHT_FREQ) ?: "Daily briefing"
    fun setInsightFrequency(value: String) = settings.putString(KEY_INSIGHT_FREQ, value)

    /** The chosen profile avatar (1-based), or 0 for the initial-letter avatar. */
    fun avatarId(): Int = settings.getInt(KEY_AVATAR, 0)
    fun setAvatarId(id: Int) = settings.putInt(KEY_AVATAR, id.coerceAtLeast(0))

    private companion object {
        const val KEY_AVATAR = "profile_avatar"
        const val KEY_DARK = "dark_mode"
        const val KEY_ALERTS = "budget_alerts"
        const val KEY_VOICE = "voice_bubble"
        const val KEY_CYCLE_START = "fiscal_cycle_start"
        const val KEY_INSIGHT_FREQ = "insight_frequency"
    }
}

/** The stored-but-inert switches on Settings. See [AppPrefs.preview]. */
enum class PreviewToggle(val key: String, val default: Boolean) {
    AutonomousGuard("preview_autonomous_guard", true),
    NeuralCategorization("preview_neural_categorization", true),
    SmartCapRollover("preview_smart_cap_rollover", false),
    BiometricShield("preview_biometric_shield", false),
    FrostedGlass("preview_frosted_glass", true),
    TactileHaptics("preview_tactile_haptics", true)
}
