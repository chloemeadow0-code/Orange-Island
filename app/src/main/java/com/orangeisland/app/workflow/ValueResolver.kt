package com.orangeisland.app.workflow

import com.orangeisland.app.model.NodeValue

/**
 * Resolves a [NodeValue] to a concrete string at execution time.
 *
 * - [NodeValue.Literal] → its raw value.
 * - [NodeValue.Ref]     → the output of the referenced upstream node. If that upstream node did
 *   not complete successfully the resolver throws — silently substituting an empty string would
 *   mask broken graphs (a missing or failed dependency is a real bug the user should fix, not a
 *   case to paper over). This matches the project-wide "no silent fallback" convention.
 *
 * Independent implementation. The static-or-reference parameter idea is a general workflow
 * primitive; this resolver is Orange Island's own.
 */
class ValueResolver(private val states: Map<String, NodeState>) {

    fun resolve(value: NodeValue, triggerPayload: String = ""): String = when (value) {
        is NodeValue.Literal -> value.value
        is NodeValue.Ref -> {
            when (val upstream = states[value.nodeId]) {
                null -> throw IllegalStateException("Referenced node ${value.nodeId} has no recorded state")
                is NodeState.Done -> upstream.output
                is NodeState.Skipped -> upstream.reason
                is NodeState.Errored ->
                    throw IllegalStateException("Referenced node ${value.nodeId} errored: ${upstream.message}")
                is NodeState.Running, is NodeState.Pending ->
                    throw IllegalStateException("Referenced node ${value.nodeId} has not completed")
            }
        }
    }

    /** Convenience for the common case: resolve only if the value is a Ref, otherwise return null. */
    fun referencedNodeId(value: NodeValue): String? = (value as? NodeValue.Ref)?.nodeId
}
