package com.fixmoney.ui

import com.fixmoney.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Maps a category's stored icon name onto its drawable. The old app resolved
 * this with `resources.getIdentifier(...)`; compose-resources generates typed
 * accessors instead, so the mapping has to be explicit.
 */
fun categoryIconFor(name: String): DrawableResource = when (name) {
    "ic_food" -> Res.drawable.ic_food
    "ic_transport" -> Res.drawable.ic_transport
    "ic_shopping" -> Res.drawable.ic_shopping
    "ic_health" -> Res.drawable.ic_health
    "ic_bills" -> Res.drawable.ic_bills
    "ic_entertainment" -> Res.drawable.ic_entertainment
    else -> Res.drawable.ic_other
}
