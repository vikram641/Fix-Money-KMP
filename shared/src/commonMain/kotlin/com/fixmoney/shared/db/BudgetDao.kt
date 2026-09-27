package com.fixmoney.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Query("SELECT * FROM budgets WHERE month = :month")
    fun observeForMonth(month: String): Flow<List<BudgetEntity>>

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)
}
