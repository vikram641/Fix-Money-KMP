package com.fixmoney.shared.model

/**
 * A spending category. `icon` is a drawable name (`ic_food`, ...) matching the
 * images in composeResources, exactly as the old app's Firestore documents
 * stored it.
 */
data class Category(
    val id: String,
    val name: String,
    val color: String,
    val icon: String
)

/**
 * TEMPORARY source for the onboarding budget screen's category chips.
 *
 * In the old app these come from Firestore, cached in Room (see the old
 * project's CLAUDE.md, "Categories now come from Firestore"). Neither Room nor
 * a KMP Firestore client exists here yet, so this hardcodes the same 7 default
 * documents that were seeded there - same ids, names, colours and icon names -
 * so the screen behaves identically in the meantime.
 *
 * Replace with the Room/Firestore-backed list when the data layer lands; every
 * consumer already deals in [Category], so nothing else should need to change.
 */
object DefaultCategories {
    val ALL = listOf(
        Category("69dab76ce89baeef7e6c86e5", "Food", "#FF6B6B", "ic_food"),
        Category("69dab76ce89baeef7e6c86e6", "Transport", "#4ECDC4", "ic_transport"),
        Category("69dab76ce89baeef7e6c86e7", "Shopping", "#45B7D1", "ic_shopping"),
        Category("69dab76ce89baeef7e6c86e8", "Health", "#96CEB4", "ic_health"),
        Category("69dab76ce89baeef7e6c86e9", "Bills", "#FFEAA7", "ic_bills"),
        Category("69dab76ce89baeef7e6c86ea", "Entertainment", "#DDA0DD", "ic_entertainment"),
        Category("69dab76ce89baeef7e6c86eb", "Other", "#B0BEC5", "ic_other")
    )
}
