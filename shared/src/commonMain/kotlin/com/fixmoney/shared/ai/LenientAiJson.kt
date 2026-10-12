package com.fixmoney.shared.ai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt

/**
 * Decoding for the *model's* JSON, which is not always the JSON the prompt asked
 * for. By the time it is parsed the Worker has already charged the coin, so a
 * strict decode that throws on a code fence or `2598.61` turned a perfectly good
 * answer into "couldn't reach the assistant" - and the user paid for it.
 */
internal val LenientAiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** The outermost `{...}`, dropping code fences or prose the model wrapped around it. */
internal fun extractJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start >= 0 && end > start) text.substring(start, end + 1) else text
}

/** Accepts 250, 250.6, "250" and "₹1,250" for an integer amount field; rounds. */
internal object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement()
        val raw = (element as? JsonPrimitive)?.contentOrNull ?: return 0
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == '-' }
        val value = cleaned.toDoubleOrNull() ?: return 0
        if (value.isNaN() || value.isInfinite()) return 0
        return value.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).roundToInt()
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** A real calendar date as `yyyy-MM-dd`, or null. "2026-02-30" and "26/10/2026" are rejected. */
internal fun validIsoDateOrNull(value: String?): String? {
    val v = value?.trim() ?: return null
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(v) ?: return null
    val (y, mo, d) = m.destructured.toList().map { it.toInt() }
    if (y < 2000 || y > 2100 || mo !in 1..12) return null
    val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[mo - 1]
    return if (d in 1..days) v else null
}
