package com.fixmoney.shared.expense

import com.fixmoney.shared.db.isIn

import com.fixmoney.shared.db.amountValue

import com.fixmoney.shared.db.BudgetDao
import com.fixmoney.shared.db.BudgetEntity
import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One row of the Budget screen: the limit, and how much of it is spent. */
data class BudgetProgress(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val limit: Int,
    val spent: Int
) {
    /** Clamped to 0..100 - what a progress bar's fill can actually express. */
    val percent: Int
        get() = rawPercent.coerceIn(0, 100)

    /**
     * **Uncapped.** The clamped one cannot tell "exactly at the cap" from
     * "double it", and the status label has to.
     */
    val rawPercent: Int
        get() = if (limit <= 0) 0 else ((spent * 100L) / limit).toInt()

    val remaining: Int get() = limit - spent
    val isOver: Boolean get() = spent > limit
}

class BudgetRepository(
    private val budgets: BudgetDao,
    private val expenses: ExpenseDao
) {

    /**
     * `spentAmount` is a stored column in the old schema, but keeping it in
     * sync needs a write on every expense change. Computing it from the
     * expenses table instead means it can never drift.
     */
    fun observeForMonth(month: String): Flow<List<BudgetProgress>> =
        combine(
            budgets.observeForMonth(month),
            expenses.observeExpensesForMonth(month)
        ) { budgetRows, expenseRows ->

            budgetRows.map { budget ->
                BudgetProgress(
                    id = budget.id,
                    categoryId = budget.categoryId,
                    categoryName = budget.categoryName,
                    categoryColor = budget.categoryColor,
                    categoryIcon = budget.categoryIcon,
                    limit = budget.limitAmount,
                    // Only expenses in the budget's own currency count against it.
                    spent = expenseRows
                        .filter { it.categoryId == budget.categoryId && it.isIn(budget.currency.ifBlank { it.currency }) }
                        .sumOf { it.amountValue }
                )
            }
        }

    suspend fun setBudget(category: Category, limit: Int, month: String, currency: String) {
        budgets.upsert(
            BudgetEntity(
                id = "local-${category.id}-$month",
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.color,
                categoryIcon = category.icon,
                currency = currency,
                limitAmount = limit,
                month = month
            )
        )
    }

    /** Splits a single figure across categories, as onboarding's budget step does. */
    suspend fun setSplitBudget(
        categories: List<Category>,
        total: Int,
        month: String,
        currency: String
    ) {
        if (categories.isEmpty()) return
        val each = total / categories.size
        // Integer division loses the remainder, so 25,000 over 7 categories
        // rolled up to 24,997 on the Budget screen while Home - which reads the
        // raw preference - still said 25,000. The leftover goes to the first
        // row so the two screens agree.
        val remainder = total - each * categories.size
        budgets.upsertAll(
            categories.mapIndexed { index, category ->
                BudgetEntity(
                    id = "local-${category.id}-$month",
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryColor = category.color,
                    categoryIcon = category.icon,
                    currency = currency,
                    limitAmount = if (index == 0) each + remainder else each,
                    month = month
                )
            }
        )
    }

    suspend fun delete(id: String) = budgets.deleteById(id)
}
