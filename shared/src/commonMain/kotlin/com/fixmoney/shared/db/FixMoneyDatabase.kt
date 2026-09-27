package com.fixmoney.shared.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * Room, shared across both platforms (Room supports KMP from 2.7).
 *
 * `expenses` and `budgets` so far; the old database also had categories and
 * the two pending-sync queues. They get added as their screens are migrated.
 */
@Database(entities = [ExpenseEntity::class, BudgetEntity::class], version = 2)
@ConstructedBy(FixMoneyDatabaseConstructor::class)
abstract class FixMoneyDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
}

/** Room's KSP processor generates the `actual` implementations per platform. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object FixMoneyDatabaseConstructor : RoomDatabaseConstructor<FixMoneyDatabase> {
    override fun initialize(): FixMoneyDatabase
}

internal const val DB_FILE_NAME = "fixmoney.db"
