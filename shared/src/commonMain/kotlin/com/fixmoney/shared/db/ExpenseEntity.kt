package com.fixmoney.shared.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Port of the old app's `data/local/ExpenseEntity.kt`. Column names and types
 * are kept identical so an existing install's `expenses` table is readable by
 * this build - the app ships as an update to the same Play Store listing.
 *
 * The API's nested category is flattened into plain columns, as before.
 * `localCreatedAt` no longer defaults to `System.currentTimeMillis()` (JVM-only);
 * callers pass it in.
 */
@Entity(
    tableName = "expenses",
    // Home reads a date window and sorts by `updatedAt` on every change, and
    // Expenses filters by month. Without these three the table was scanned and
    // re-sorted in full each time - invisible at a few hundred rows, and the
    // first thing a multi-year user feels.
    indices = [
        Index("date"),
        Index("updatedAt"),
        Index("categoryId"),
    ]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amount: String,
    val currency: String,
    val date: String,
    val note: String,
    val receiptUrl: String? = "",
    val createdAt: String,
    val updatedAt: String,
    val isSynced: Boolean = false,
    val localCreatedAt: Long,
    val userId: String = "",

    // flattened category
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String
)
