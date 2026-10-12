package com.fixmoney.shared.receipt

/**
 * How readable the photo turned out. Carried over from the old app unchanged -
 * the two failure cases are deliberately *soft*: a result still comes back so
 * the scanner can say what went wrong and offer Retake, rather than looking
 * like a crash.
 */
enum class ReceiptImageStatus { OK, BLURRY, NOT_A_RECEIPT }

/**
 * One scanned receipt, in the shape Add Expense can apply field by field.
 * Every field is independently optional - a partial read is still useful.
 *
 * [amount] is a string rather than an Int because that is what the amount field
 * holds, and because "absent" and "zero" have to stay distinguishable.
 */
data class ExtractedReceipt(
    val amount: String? = null,
    val date: String? = null,
    val note: String? = null,
    val category: String? = null,
    /** ISO 4217 code printed on the receipt, if the model could tell. */
    val currency: String? = null,
    val status: ReceiptImageStatus = ReceiptImageStatus.OK
)
