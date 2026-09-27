package com.fixmoney.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.fixmoney.shared.onboarding.OnboardingPrefs

/**
 * State shared across the onboarding screens as the user fills them in -
 * the old `OnboardingViewModel`, which was `activityViewModels()`-scoped.
 *
 * Defaults match the old app: ₹25,000 and INR.
 */
class OnboardingState(private val prefs: OnboardingPrefs) {
    var name by mutableStateOf("")
    var monthlyBudget by mutableIntStateOf(25_000)
    var currencyCode by mutableStateOf("INR")
    val selectedCategoryIds = mutableListOf<String>().toMutableStateList()

    fun toggleCategory(id: String) {
        if (!selectedCategoryIds.remove(id)) selectedCategoryIds.add(id)
    }

    /**
     * The old ViewModel also wrote per-category `BudgetEntity` rows here, so the
     * collected number reached the Budget screen and Home's budget card. That
     * half needs Room, which does not exist in this project yet - the profile
     * itself is saved exactly as before.
     */
    fun complete() {
        prefs.saveProfile(
            name = name.trim().ifEmpty { "there" },
            monthlyBudget = monthlyBudget,
            currencyCode = currencyCode
        )
    }
}
