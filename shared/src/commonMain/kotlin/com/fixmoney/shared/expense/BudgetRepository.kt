package com.fixmoney.shared.expense

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
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val limit: Int,
    val spent: Int
) {
    val percent: Int
        get() = if (limit <= 0) 0 else ((spent * 100L) / limit).toInt().coerceIn(0, 100)
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
            val spentByCategory = expenseRows
                .groupBy { it.categoryId }
                .mapValues { (_, rows) -> rows.sumOf { it.amount.toIntOrNull() ?: 0 } }

            budgetRows.map { budget ->
                BudgetProgress(
                    id = budget.id,
                    categoryName = budget.categoryName,
                    categoryColor = budget.categoryColor,
                    categoryIcon = budget.categoryIcon,
                    limit = budget.limitAmount,
                    spent = spentByCategory[budget.categoryId] ?: 0
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
        budgets.upsertAll(
            categories.map { category ->
                BudgetEntity(
                    id = "local-${category.id}-$month",
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryColor = category.color,
                    categoryIcon = category.icon,
                    currency = currency,
                    limitAmount = each,
                    month = month
                )
            }
        )
    }

    suspend fun delete(id: String) = budgets.deleteById(id)
}
