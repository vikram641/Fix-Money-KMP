package com.fixmoney.shared.onboarding

import com.russhwolf.settings.Settings

/**
 * Local-only first-run profile (name / monthly budget / currency), and the flag
 * that decides whether the app opens onboarding or the dashboard.
 *
 * KMP port of the old app's `core/util/OnboardingPrefs.kt`, which wrapped
 * SharedPreferences. multiplatform-settings gives the same key/value API backed
 * by SharedPreferences on Android and NSUserDefaults on iOS, so the keys and
 * semantics are unchanged - an Android user who already onboarded keeps their
 * profile when this build replaces the old one.
 */
class OnboardingPrefs(private val settings: Settings) {

    fun isOnboardingComplete(): Boolean = settings.getBoolean(KEY_COMPLETE, false)

    fun saveProfile(name: String, monthlyBudget: Int, currencyCode: String) {
        settings.putString(KEY_NAME, name)
        settings.putInt(KEY_BUDGET, monthlyBudget)
        settings.putString(KEY_CURRENCY, currencyCode)
        settings.putBoolean(KEY_COMPLETE, true)
    }

    fun getUserName(): String? = settings.getStringOrNull(KEY_NAME)
    fun getMonthlyBudget(): Int = settings.getInt(KEY_BUDGET, 0)
    fun getCurrencyCode(): String = settings.getStringOrNull(KEY_CURRENCY) ?: "INR"

    /** Full reset - used by Settings > Logout, which restarts onboarding. */
    fun clear() = settings.clear()

    private companion object {
        // Same key names the old Android app wrote, deliberately.
        const val KEY_COMPLETE = "onboarding_complete"
        const val KEY_NAME = "user_name"
        const val KEY_BUDGET = "monthly_budget"
        const val KEY_CURRENCY = "currency_code"
    }
}
