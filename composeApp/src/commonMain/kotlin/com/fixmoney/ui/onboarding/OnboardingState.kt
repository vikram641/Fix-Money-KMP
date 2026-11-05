package com.fixmoney.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.fixmoney.shared.data.CategoryRepository
import com.fixmoney.shared.expense.BudgetRepository
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.fixmoney.shared.platform.currentMonthApi

/**
 * State shared across the onboarding screens as the user fills them in -
 * the old `OnboardingViewModel`, which was `activityViewModels()`-scoped.
 *
 * Defaults match the old app: ₹25,000 and INR.
 */
class OnboardingState(
    private val prefs: OnboardingPrefs,
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository
) {
    var name by mutableStateOf("")
    var monthlyBudget by mutableIntStateOf(25_000)
    var currencyCode by mutableStateOf("INR")
    val selectedCategoryIds = mutableListOf<String>().toMutableStateList()

    fun toggleCategory(id: String) {
        if (!selectedCategoryIds.remove(id)) selectedCategoryIds.add(id)
    }

    /**
     * Saves the profile **and** splits the monthly figure into per-category
     * `BudgetEntity` rows for the current month, which is what the old
     * `completeOnboarding()` did. Without that second half the number collected
     * here reached Home's card (it reads the raw preference) but the Budget
     * screen stayed empty, because that screen only ever reads budget rows.
     *
     * The split covers whichever categories were ticked on the budget step, or
     * **all** of them when none were - the step calls that row optional, so an
     * untouched one must not mean "no budget anywhere".
     *
     * Suspending, and called from the Start Tracking click: the Room write has
     * to finish, but navigation does not wait on it - Budget observes the table
     * and picks the rows up whenever they land.
     */
    suspend fun complete() {
        prefs.saveProfile(
            name = name.trim().ifEmpty { "there" },
            monthlyBudget = monthlyBudget,
            currencyCode = currencyCode
        )

        if (monthlyBudget <= 0) return
        val all = categories.getCategories()
        val chosen = all.filter { it.id in selectedCategoryIds }.ifEmpty { all }
        budgets.setSplitBudget(
            categories = chosen,
            total = monthlyBudget,
            month = currentMonthApi(),
            currency = currencyCode
        )
    }
}
