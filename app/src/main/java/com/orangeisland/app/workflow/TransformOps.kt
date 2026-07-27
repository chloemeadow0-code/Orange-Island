package com.orangeisland.app.workflow

import com.orangeisland.app.model.TransformOp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

/**
 * Pure implementations of the seven [TransformOp] variants. The engine resolves all
 * [com.orangeisland.app.model.NodeValue]s to strings first, then hands the materialized inputs
 * here. Each function returns the shaped output (or a fallback) — never throws on a no-match,
 * because a transform that finds nothing is a legitimate "produce the default" outcome, not a
 * graph error. Bad config (e.g. an invalid regex) does throw, since that is a user-fixable bug.
 *
 * Uses kotlinx.serialization (not org.json) for JSON-path so the engine stays pure-JVM and
 * unit-testable without the Android jar.
 *
 * Independent implementation.
 */
object TransformOps {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Cleans a possibly-messy LLM string down to its bare JSON payload so [json.parseToJsonElement]
     * can succeed. LLMs (especially smaller / non-OpenAI-compatible ones) frequently wrap JSON in
     * markdown fences, lead with explanatory prose, or trail conversational text — any of which
     * makes strict parsing throw and the whole downstream chain collapse into the fallback. This:
     *   1. strips ``` / ```json fences,
     *   2. if the result still isn't pure JSON, extracts the outermost {…} or […] substring,
     *   3. leaves already-clean JSON untouched.
     * Returns the cleaned string (which may still fail to parse if there was no JSON at all).
     */
    fun coerceToJson(input: String): String {
        var s = input.trim()
        // 1. Strip a single markdown fence. Handles ```json\n...\n``` and bare ``` ... ```.
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            // Drop an optional language tag on the opening fence (json, JSON, etc.).
            if (s.regionThatStartsWith("json", ignoreCase = true)) s = s.substring(4)
            val lastFence = s.lastIndexOf("```")
            if (lastFence >= 0) s = s.substring(0, lastFence)
            s = s.trim()
        }
        // 2. If it still isn't a JSON value, carve out the outermost balanced object/array. This
        //    rescues "Sure, here you go: { ... }." and trailing commentary. Only { } / [ ] are
        //    considered; strings inside are skipped over so a '}' inside a JSON string value won't
        //    terminate the carve early.
        if (s.isNotEmpty() && s.first() !in "{[") {
            val carved = carveOutermostJson(s)
            if (carved != null) s = carved
        }
        return s
    }

    /** Walks [text] for the first balanced {…}/[…] and returns that substring, honouring string
     *  literals and escapes so braces inside strings are ignored. Null if no balanced span found. */
    private fun carveOutermostJson(text: String): String? {
        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val open = text[start]
        val close = when (open) {
            '{' -> '}'
            else -> ']'
        }
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun String.regionThatStartsWith(prefix: String, ignoreCase: Boolean): Boolean =
        length >= prefix.length && substring(0, prefix.length).equals(prefix, ignoreCase = ignoreCase)

    fun apply(op: TransformOp, input: String): String = when (op) {
        is TransformOp.Regex -> applyRegex(input, op)
        is TransformOp.JsonPath -> applyJsonPath(input, op)
        is TransformOp.Slice -> applySlice(input, op)
        is TransformOp.Join -> input   // Join's extras were already concatenated by the engine
        is TransformOp.RandomInt -> op.fixed?.toIntOrNull()?.toString() ?: randomInt(op.min, op.max).toString()
        is TransformOp.RandomText -> op.fixed ?: randomText(op.length, op.charset)
        is TransformOp.Fixed -> op.value
    }

    private fun applyRegex(input: String, op: TransformOp.Regex): String {
        if (op.pattern.isBlank()) return op.fallback
        return try {
            val match = kotlin.text.Regex(op.pattern).find(input)
            match?.groups?.get(op.group)?.value ?: op.fallback
        } catch (_: Exception) {
            op.fallback
        }
    }

    private fun applyJsonPath(input: String, op: TransformOp.JsonPath): String {
        if (op.path.isBlank()) return op.fallback
        // LLM output is frequently wrapped in markdown fences or lead with prose; coerce to bare
        // JSON before parsing so a non-conformant wrapper doesn't collapse the whole chain.
        val root: JsonElement = try { json.parseToJsonElement(coerceToJson(input)) }
            catch (_: Exception) { return op.fallback }
        // Walk a dotted path with optional [index] segments, e.g. "data.items[0].name".
        var current: JsonElement? = root
        for (segment in op.path.split('.').filter { it.isNotBlank() }) {
            if (current == null) return op.fallback
            val name = segment.substringBefore('[')
            if (name.isNotBlank()) {
                current = (current as? JsonObject)?.get(name)
            }
            segment.substringAfter('[', "").split('[').filter { it.endsWith(']') }.forEach { bracketed ->
                val idx = bracketed.removeSuffix("]").toIntOrNull() ?: return@forEach
                current = (current as? JsonArray)?.getOrNull(idx)
            }
            if (current == null) return op.fallback
        }
        return current?.stringify() ?: op.fallback
    }

    /** Render a leaf JsonElement back to its string form (unquoted primitive, JSON for object/array). */
    private fun JsonElement.stringify(): String = when (this) {
        is JsonPrimitive -> contentOrNull ?: ""
        is JsonObject -> toString()
        is JsonArray -> toString()
        else -> toString()
    }

    private fun applySlice(input: String, op: TransformOp.Slice): String {
        if (input.isEmpty() || op.start < 0 || op.start > input.length) return op.fallback
        val endExclusive = if (op.length < 0) input.length else minOf(op.start + op.length, input.length)
        if (endExclusive < op.start) return op.fallback
        return input.substring(op.start, endExclusive)
    }

    private fun randomInt(min: Int, max: Int): Int {
        val low = minOf(min, max); val high = maxOf(min, max)
        if (low == high) return low
        return Random.nextLong(low.toLong(), (high + 1).toLong()).toInt()
    }

    private fun randomText(length: Int, charset: String): String {
        val safeLen = length.coerceAtLeast(0)
        if (safeLen == 0) return ""
        val chars = if (charset.isNotEmpty()) charset else TransformOp.RandomText.ALNUM
        return buildString(safeLen) { repeat(safeLen) { append(chars[Random.nextInt(chars.length)]) } }
    }
}
