package com.fixmoney.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The subset of the old `ExpenseDao` the migrated screens actually use so far. */
@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    /** Reactive - Home and the expense list re-render when a row changes. */
    @Query("SELECT * FROM expenses ORDER BY updatedAt DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY updatedAt DESC")
    suspend fun getExpensesOnce(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    /** `date` is stored as `yyyy-MM-dd`, so a month is a plain prefix match. */
    /**
     * A bounded window, for Home.
     *
     * Home used to observe the whole table and re-derive from it on every
     * change. Correct, and fine at a few hundred rows; a multi-year user with
     * tens of thousands pays for all of them on every insert. The window has to
     * reach back past the selected month because the insight baseline is the
     * three weeks ending a week ago, which early in a month sits entirely in the
     * previous one - see HomeState.observe.
     */
    // No ORDER BY: Home re-sorts for its "Recent" list and otherwise only sums,
    // groups and range-filters, so ordering here bought nothing and forced a
    // temp B-tree on top of the indexed range scan.
    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to")
    fun observeExpensesBetween(from: String, to: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC")
    fun observeExpensesForMonth(monthPrefix: String): Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)
}
