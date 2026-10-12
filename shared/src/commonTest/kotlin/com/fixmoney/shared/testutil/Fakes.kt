package com.fixmoney.shared.testutil

import com.fixmoney.shared.db.BudgetDao
import com.fixmoney.shared.db.BudgetEntity
import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.onboarding.OnboardingPrefs
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeExpenseDao(initial: List<ExpenseEntity> = emptyList()) : ExpenseDao {
    val rows = MutableStateFlow(initial)
    override suspend fun insertExpense(expense: ExpenseEntity) { rows.value = rows.value.filterNot { it.id == expense.id } + expense }
    override fun observeExpenses(): Flow<List<ExpenseEntity>> = rows
    override suspend fun getExpensesOnce(): List<ExpenseEntity> = rows.value
    override suspend fun getExpenseById(id: String): ExpenseEntity? = rows.value.firstOrNull { it.id == id }
    override fun observeExpensesBetween(from: String, to: String): Flow<List<ExpenseEntity>> =
        rows.map { list -> list.filter { it.date >= from && it.date <= to } }
    override fun observeExpensesForMonth(monthPrefix: String): Flow<List<ExpenseEntity>> =
        rows.map { list -> list.filter { it.date.startsWith(monthPrefix) } }
    override suspend fun deleteById(id: String) { rows.value = rows.value.filterNot { it.id == id } }
}

class FakeBudgetDao(initial: List<BudgetEntity> = emptyList()) : BudgetDao {
    val rows = MutableStateFlow(initial)
    override suspend fun upsert(budget: BudgetEntity) { rows.value = rows.value.filterNot { it.id == budget.id } + budget }
    override suspend fun upsertAll(budgets: List<BudgetEntity>) { budgets.forEach { upsert(it) } }
    override fun observeForMonth(month: String): Flow<List<BudgetEntity>> = rows.map { l -> l.filter { it.month == month } }
    override suspend fun deleteById(id: String) { rows.value = rows.value.filterNot { it.id == id } }
}

fun prefs(currency: String = "INR", budget: Int = 25_000, name: String = "Alex"): OnboardingPrefs =
    OnboardingPrefs(MapSettings()).apply { saveProfile(name, budget, currency) }

private var seq = 0L

/** One expense row; amount as stored (a string), category by name. */
fun expense(date: String, amount: String, category: String = "Food", currency: String = "INR", note: String = ""): ExpenseEntity {
    seq++
    return ExpenseEntity(
        id = "t-$seq", amount = amount, currency = currency, date = date, note = note,
        receiptUrl = "", createdAt = date, updatedAt = date, isSynced = false,
        localCreatedAt = seq, userId = "", categoryId = "cat-$category",
        categoryName = category, categoryColor = "#FF0000", categoryIcon = "ic_food"
    )
}

fun budget(category: String, limit: Int, month: String, currency: String = "INR") = BudgetEntity(
    id = "b-$category-$month", categoryId = "cat-$category", categoryName = category,
    categoryColor = "#FF0000", categoryIcon = "ic_food", currency = currency,
    limitAmount = limit, month = month, spentAmount = 0
)
