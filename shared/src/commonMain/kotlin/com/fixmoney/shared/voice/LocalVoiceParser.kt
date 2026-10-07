package com.fixmoney.shared.voice

import com.fixmoney.shared.ai.ParsedVoiceExpense

/**
 * Reads the easy voice phrases on the phone, so they cost no coin and no AI call.
 *
 * "150 coffee", "spent 40 on auto", "₹500 groceries", "1200 electricity bill" -
 * one number and a word or two that plainly name a category - make up most of
 * what people say to the bubble, and the model adds nothing for them. Anything
 * less clear-cut ([Result.Unsure]) still goes to [com.fixmoney.shared.ai.VoiceExpenseParser]
 * exactly as before: two numbers, a decimal, number words, a sentence the
 * keyword list does not cover. Erring towards the model is the point - a wrong
 * local guess is a wrong row in the user's ledger, a needless model call is
 * one coin.
 *
 * Also answers [Result.NoAmount] when the speech has no number in any form,
 * which used to cost a coin for the model to say "amount 0".
 */
object LocalVoiceParser {

    sealed interface Result {
        data class Parsed(val expense: ParsedVoiceExpense) : Result
        /** Nothing in the speech could be an amount - no digits, no number words. */
        data object NoAmount : Result
        /** Hand it to the model. */
        data object Unsure : Result
    }

    private const val MAX_AMOUNT = 10_000_000
    private const val MAX_NOTE_WORDS = 4

    private val NUMBER = Regex("""\d[\d,]*(\.\d+)?k?""")

    /** Currency words and the glue of "spent 40 on auto" / "auto ke 40 diye". */
    private val FILLER = setOf(
        "rs", "rs.", "inr", "rupee", "rupees", "rupay", "rupaye", "rupiya", "rupiye", "₹",
        "on", "for", "of", "spent", "spend", "paid", "pay", "add", "added", "expense",
        "the", "a", "an", "to", "at", "in", "my",
        "ka", "ki", "ke", "ko", "me", "mein", "pe", "par", "diye", "diya", "kharch", "kharcha", "kiya", "liye"
    )

    /** Words that change what the number means. Any of these and the model decides. */
    private val AMBIGUOUS = setOf(
        "minus", "plus", "split", "half", "each", "per", "times", "x", "and", "aur", "or",
        "yesterday", "kal", "refund", "received", "got", "income", "salary"
    )

    /** Spoken numbers. With any of these the amount is not just the digits. */
    private val NUMBER_WORDS = setOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "fifteen", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
        "eighty", "ninety", "hundred", "thousand", "lakh", "lakhs", "crore", "million",
        "ek", "do", "teen", "char", "chaar", "paanch", "panch", "das", "bees", "pachas", "pachaas",
        "sau", "hazaar", "hazar", "hajar"
    )

    /**
     * Keyword -> category name. Used only when a category of that name exists,
     * and only after the spoken words were checked against the real names.
     */
    private val KEYWORDS: Map<String, String> = buildMap {
        fun map(category: String, vararg words: String) = words.forEach { this[it] = category }
        map(
            "Food",
            "food", "coffee", "chai", "tea", "lunch", "dinner", "breakfast", "snack", "snacks",
            "swiggy", "zomato", "pizza", "burger", "restaurant", "cafe", "grocery", "groceries",
            "sabzi", "vegetables", "fruits", "milk", "doodh", "biryani", "maggi", "juice", "khana",
            "nashta", "dosa", "samosa", "momos", "blinkit", "zepto", "bigbasket", "icecream",
            "sweets", "mithai", "bread", "eggs"
        )
        map(
            "Transport",
            "auto", "uber", "ola", "rapido", "cab", "taxi", "petrol", "diesel", "fuel", "metro",
            "bus", "train", "flight", "parking", "toll", "rickshaw", "fastag", "cng"
        )
        map(
            "Shopping",
            "shopping", "clothes", "shirt", "tshirt", "jeans", "shoes", "dress", "amazon",
            "flipkart", "myntra", "meesho", "gift", "gifts"
        )
        map("Health", "medicine", "medicines", "doctor", "pharmacy", "hospital", "gym", "dawai", "chemist", "clinic")
        map(
            "Bills",
            "bill", "bills", "electricity", "recharge", "rent", "wifi", "internet", "broadband",
            "emi", "insurance", "dth"
        )
        map("Entertainment", "movie", "movies", "netflix", "hotstar", "spotify", "cinema", "concert", "party")
    }

    fun parse(spokenText: String, categoryNames: List<String>): Result {
        val text = spokenText.lowercase().replace("₹", " ₹ ").replace("/-", " ")
        val numbers = NUMBER.findAll(text).map { it.value }.toList()
        // Punctuation the recogniser adds ("coffee.") must not hide a keyword.
        val words = NUMBER.replace(text, " ").split(Regex("""[^\p{L}₹.]+"""))
            .map { it.trim('.') }
            .filter { it.isNotEmpty() }

        if (numbers.isEmpty()) {
            return if (words.any { it in NUMBER_WORDS }) Result.Unsure else Result.NoAmount
        }
        if (numbers.size != 1 || words.any { it in NUMBER_WORDS || it in AMBIGUOUS }) return Result.Unsure

        val amount = amountOf(numbers.single()) ?: return Result.Unsure
        if (amount !in 1..MAX_AMOUNT) return Result.Unsure

        val described = words.filter { it !in FILLER }
        if (described.size > MAX_NOTE_WORDS) return Result.Unsure

        val byName = categoryNames.associateBy { it.lowercase() }
        val matched = described.mapNotNull { word ->
            byName[word] ?: KEYWORDS[word]?.let { byName[it.lowercase()] }
        }.toSet()

        val category = when {
            // Just a number: what the model would have done anyway - "Other",
            // with nothing to call it.
            described.isEmpty() -> byName["other"] ?: return Result.Unsure
            matched.size == 1 -> matched.single()
            else -> return Result.Unsure
        }
        val note = described.joinToString(" ").replaceFirstChar { it.uppercase() }
        return Result.Parsed(ParsedVoiceExpense(amount, category, note))
    }

    /** "1,200" -> 1200, "2k" -> 2000. A decimal goes to the model: amounts are whole rupees. */
    private fun amountOf(raw: String): Int? {
        if ('.' in raw) return null
        val thousands = raw.endsWith("k")
        val digits = raw.removeSuffix("k").replace(",", "")
        val n = digits.toLongOrNull() ?: return null
        val total = if (thousands) n * 1000 else n
        return if (total > Int.MAX_VALUE) null else total.toInt()
    }
}
