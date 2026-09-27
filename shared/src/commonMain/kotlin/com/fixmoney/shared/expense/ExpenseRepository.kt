package com.fixmoney.shared.expense

import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.model.Category
import com.fixmoney.shared.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

/**
 * Local-only for now. The old app wrote here and queued a sync row for the
 * server; there is no backend in this build yet (the old one had been running
 * fully offline too - see the old project's CLAUDE.md, "Offline mode").
 */
class ExpenseRepository(private val dao: ExpenseDao) {

    fun observeExpenses(): Flow<List<ExpenseEntity>> = dao.observeExpenses()

    fun observeMonth(monthPrefix: String): Flow<List<ExpenseEntity>> =
        dao.observeExpensesForMonth(monthPrefix)

    suspend fun addExpense(
        amount: Int,
        currency: String,
        dateIso: String,
        note: String,
        category: Category
    ) {
        val now = currentTimeMillis()
        dao.insertExpense(
            ExpenseEntity(
                // No UUID in common Kotlin; the timestamp plus the category is
                // unique enough for a local row, and the server assigned real
                // ids in the old app anyway.
                id = "local-$now",
                // Stored as a string, as the old schema did.
                amount = amount.toString(),
                currency = currency,
                date = dateIso,
                note = note,
                receiptUrl = "",
                createdAt = dateIso,
                updatedAt = dateIso,
                isSynced = false,
                localCreatedAt = now,
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.color,
                categoryIcon = category.icon
            )
        )
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
