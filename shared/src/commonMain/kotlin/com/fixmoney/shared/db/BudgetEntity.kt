package com.fixmoney.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Port of the old `BudgetEntity` - same columns, so existing rows still read. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val currency: String,
    val limitAmount: Int,
    /** `yyyy-MM`, the same month key the rest of the app uses. */
    val month: String,
    val spentAmount: Int = 0
)
