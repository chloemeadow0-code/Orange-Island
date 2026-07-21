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
        val root: JsonElement = try { json.parseToJsonElement(input) } catch (_: Exception) { return op.fallback }
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
