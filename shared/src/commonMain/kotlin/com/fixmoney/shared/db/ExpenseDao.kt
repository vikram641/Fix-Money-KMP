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
    @Query("SELECT * FROM expenses WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC")
    fun observeExpensesForMonth(monthPrefix: String): Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)
}
