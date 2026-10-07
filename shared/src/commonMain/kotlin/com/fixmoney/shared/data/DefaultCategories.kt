package com.fixmoney.shared.data

import com.fixmoney.shared.model.Category

object DefaultCategories {
    val ALL = listOf(
        Category("food", "Food", "#FF6B6B", "ic_food"),
        Category("transport", "Transport", "#4ECDC4", "ic_transport"),
        Category("shopping", "Shopping", "#45B7D1", "ic_shopping"),
        Category("health", "Health", "#96CEB4", "ic_health", ),
        Category("bills", "Bills", "#FFEAA7", "ic_bills"),
        Category("entertainment", "Entertainment", "#DDA0DD", "ic_entertainment"),
        Category("other", "Other", "#B0BEC5", "ic_other")
    )
}