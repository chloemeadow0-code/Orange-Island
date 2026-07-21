package com.orangeisland.app.workflow

import com.orangeisland.app.model.Comparison
import com.orangeisland.app.model.EdgeGuard

/**
 * Two pure helpers the engine calls per node:
 *  - [compare] evaluates a [Comparison] operator against two strings (numeric-aware).
 *  - [edgeFires] decides whether an [EdgeGuard] is satisfied by a source node's terminal state.
 *
 * Pure-JVM (no org.json, no Android) so the engine is unit-testable as-is. List parsing for the
 * IN/NOT_IN operators accepts either a JSON-string-array shape (`["a","b"]`) or a plain comma
 * list (`a,b`), both handled by a small string parser below.
 *
 * Independent implementation. Comparison semantics (numeric when both sides parse as doubles,
 * else lexicographic, type-mismatch is an error rather than a silent coercion) and the four
 * guard kinds are Orange Island's own design.
 */
object ConditionEvaluator {

    /**
     * Compare two raw strings under [cmp]. Numeric when both parse strictly as doubles (so "2" >
     * "10" is true, not lexicographically false); otherwise string compare for EQ/NE/CONTAINS;
     * IN/NOT_IN treat [rhs] as a JSON string array or comma list. A numeric/string mismatch (one
     * side numeric, the other not) throws rather than guess — broken comparisons should surface,
     * not silently coerce.
     */
    fun compare(lhs: String, rhs: String, cmp: Comparison): Boolean {
        val lNum = lhs.trim().toDoubleOrNull()
        val rNum = rhs.trim().toDoubleOrNull()
        return when (cmp) {
            Comparison.EQ, Comparison.NE -> {
                val bothNum = lNum != null && rNum != null
                val matched = if (bothNum) lNum == rNum
                    else if (lNum == null && rNum == null) lhs == rhs
                    else throw TypeMismatch(lhs, rhs)
                if (cmp == Comparison.EQ) matched else !matched
            }
            Comparison.GT, Comparison.GE, Comparison.LT, Comparison.LE -> {
                val (l, r) = requireNumeric(lhs, rhs, lNum, rNum)
                when (cmp) {
                    Comparison.GT -> l > r; Comparison.GE -> l >= r
                    Comparison.LT -> l < r; Comparison.LE -> l <= r
                    else -> error("unreachable")
                }
            }
            Comparison.CONTAINS -> lhs.contains(rhs)
            Comparison.NOT_CONTAINS -> !lhs.contains(rhs)
            Comparison.IN, Comparison.NOT_IN -> {
                val items = parseList(rhs)
                val contains = items.any { item ->
                    val iNum = item.trim().toDoubleOrNull()
                    if (lNum != null && iNum != null) lNum == iNum
                    else if (lNum == null && iNum == null) item == lhs
                    else false   // mixed-type items: this one can't match
                }
                if (cmp == Comparison.IN) contains else !contains
            }
        }
    }

    /**
     * Does [guard] let the edge fire, given the source node's terminal [state]?
     *  - OnSuccess needs Done; OnFailure needs Errored; Bool parses Done.output as a boolean;
     *    Regex needs Done.output to contain a match.
     *  - Skipped/Pending/Running sources never fire any guard.
     */
    fun edgeFires(guard: EdgeGuard?, state: NodeState): Boolean {
        if (guard == null) return state is NodeState.Done
        return when (guard) {
            is EdgeGuard.OnSuccess -> state is NodeState.Done
            is EdgeGuard.OnFailure -> state is NodeState.Errored
            is EdgeGuard.Bool -> (state as? NodeState.Done)?.output?.let { parseBool(it) == guard.expected } ?: false
            is EdgeGuard.Regex -> {
                val out = (state as? NodeState.Done)?.output ?: return false
                runCatching { kotlin.text.Regex(guard.pattern).containsMatchIn(out) }.getOrDefault(false)
            }
        }
    }

    /** true/false/yes/no/on/off/1/0 (case-insensitive); null if [raw] is none of those. */
    fun parseBool(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "true", "yes", "on", "1", "y" -> true
        "false", "no", "off", "0", "n" -> false
        else -> null
    }

    private fun requireNumeric(lhs: String, rhs: String, lNum: Double?, rNum: Double?): Pair<Double, Double> {
        if (lNum != null && rNum != null) return lNum to rNum
        throw TypeMismatch(lhs, rhs)
    }

    /**
     * Parse a list from [raw]: a JSON string array (`["a","b","c"]`) or a comma-separated list
     * (`a,b,c`). The JSON branch is a minimal hand-rolled scan (trim, strip the outer brackets,
     * split on commas, peel one layer of surrounding quotes per item) — enough for the
     * string-array shape a tool result actually carries, without pulling org.json into the
     * pure-JVM engine. Anything that isn't bracketed falls through to comma split.
     */
    private fun parseList(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isEmpty()) return emptyList()
            return inner.split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
        }
        return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    class TypeMismatch(lhs: String, rhs: String) :
        IllegalArgumentException("Cannot compare values of mismatched types: \"$lhs\" vs \"$rhs\"")
}
