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

    /** Bounded window - see [com.fixmoney.shared.db.ExpenseDao.observeExpensesBetween]. */
    fun observeExpensesBetween(from: String, to: String): Flow<List<ExpenseEntity>> =
        dao.observeExpensesBetween(from, to)

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
                // No UUID in common Kotlin. The millisecond alone collided when two
                // saves landed together (voice bubble + manual), and REPLACE then
                // silently overwrote one, so a random suffix rides along.
                id = "local-$now-${kotlin.random.Random.nextLong().toULong().toString(36)}",
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

    /**
     * Rewrites an existing row in place. The DAO inserts with
     * `OnConflictStrategy.REPLACE`, so re-inserting the same primary key is the
     * update - there is no separate `@Update` to keep in step.
     *
     * [ExpenseEntity.createdAt] and [ExpenseEntity.localCreatedAt] are carried
     * over from the original rather than reset: they are when the expense was
     * *recorded*, and editing an amount does not change that. Only `updatedAt`
     * moves.
     */
    suspend fun updateExpense(
        existing: ExpenseEntity,
        amount: Int,
        currency: String,
        dateIso: String,
        note: String,
        category: Category
    ) {
        dao.insertExpense(
            existing.copy(
                amount = amount.toString(),
                currency = currency,
                date = dateIso,
                note = note,
                updatedAt = dateIso,
                isSynced = false,
                categoryId = category.id,
                categoryName = category.name,
                categoryColor = category.color,
                categoryIcon = category.icon
            )
        )
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
