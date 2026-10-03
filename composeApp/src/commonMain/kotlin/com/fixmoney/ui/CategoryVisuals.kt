package com.fixmoney.ui

import androidx.compose.ui.graphics.Color
import com.fixmoney.resources.*
import com.fixmoney.ui.home.parseHexColor
import org.jetbrains.compose.resources.DrawableResource

/** What [parseHexColor] returns when it cannot read the string. */
private val PARSE_FALLBACK = Color(0xFFB0BEC5)

/**
 * A category's accent colour: the one Firestore gave it when that is readable,
 * otherwise the redesign's palette keyed off the icon name.
 *
 * The seeded categories do carry colours, but one added later might not, and
 * `parseHexColor`'s grey fallback would put a dead slab in a chart whose whole
 * point is the palette.
 */
fun categoryTint(colorHex: String, iconName: String, colors: DesignColors): Color {
    val parsed = parseHexColor(colorHex)
    if (parsed != PARSE_FALLBACK) return parsed
    return when (iconName.lowercase().removePrefix("ic_")) {
        "food" -> colors.categoryFood
        "transport" -> colors.categoryTransit
        "shopping" -> colors.categoryShop
        else -> colors.categoryEnt
    }
}

/**
 * The line glyph for a category. The old PNG category icons are full-colour and
 * would fight the mockup's tinted-circle treatment, so the redesign maps the
 * same `icon` names onto the new stroke set and falls back to a neutral mark.
 */
fun categoryGlyph(iconName: String): DrawableResource =
    when (iconName.lowercase().removePrefix("ic_")) {
        "food" -> Res.drawable.nd_cat_food
        "transport" -> Res.drawable.nd_cat_transport
        "shopping" -> Res.drawable.nd_cat_shopping
        "bills" -> Res.drawable.nd_cat_bills
        "health" -> Res.drawable.nd_cat_health
        "entertainment", "fun" -> Res.drawable.nd_cat_fun
        "groceries", "grocery" -> Res.drawable.nd_cat_grocery
        else -> Res.drawable.nd_cat_other
    }

/**
 * The donut legend and the filter chips sit in half a card on a compact phone,
 * where a full category name ellipsises to "Sh…" and says nothing. The mockup
 * uses short labels throughout; this maps the app's real names onto the same
 * ones and leaves anything else alone.
 */
fun shortCategoryLabel(name: String): String = when (name.lowercase()) {
    "transport" -> "Transit"
    "shopping" -> "Shop"
    "entertainment" -> "Ent"
    "groceries" -> "Grocery"
    else -> name
}
