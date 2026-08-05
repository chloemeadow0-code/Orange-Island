package com.orangeisland.app.viewmodel

import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog

data class ConversationUiState(
    val path: List<ChatMessage> = emptyList(),
    val allMessages: List<ChatMessage> = emptyList(),
    val streamingMsg: ChatMessage? = null,
    val isLoading: Boolean = false,
    val selectedChildren: Map<String?, String> = emptyMap()
) {
    companion object {
        /** Walk the conversation tree to produce the visible path. */
        fun resolvePath(
            allMessages: List<ChatMessage>,
            streamingMsg: ChatMessage?,
            selectedChildren: Map<String?, String>
        ): List<ChatMessage> {
            val path = mutableListOf<ChatMessage>()
            val visited = mutableSetOf<String>()
            val allIds = allMessages.mapTo(mutableSetOf()) { it.id }
            // Diagnostic: detect when the visible path drops a large fraction of messages,
            // which is the symptom of the "conversation collapses" bug.
            val visibleTotal = allMessages.count {
                !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
            }
            val userMsgs = allMessages.filter {
                it.participant == Participant.USER &&
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                    !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
            }
            val modelMsgs = allMessages.filter {
                it.participant == Participant.MODEL &&
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                    !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
            }
            DebugLog.d("MsgDisappear", "resolvePath START: all=${allMessages.size} visible=$visibleTotal " +
                "users=${userMsgs.size} models=${modelMsgs.size} selectedKeys=${selectedChildren.size} " +
                "rootSel=${selectedChildren[null]?.take(20)}")
            allMessages.forEach { m ->
                if (m.participant == Participant.USER || m.participant == Participant.MODEL) {
                    DebugLog.d("MsgDisappear", "  msg ${m.id.take(12)} parent=${m.parentId?.take(12) ?: "null"} " +
                        "role=${m.participant} status=${m.status} ts=${m.timestamp}")
                }
            }
            selectedChildren.forEach { (pid, cid) ->
                DebugLog.d("MsgDisappear", "  selectedChildren[${pid?.take(12) ?: "null"}]=${cid.take(12)}")
            }
            var cursor: String? = null

            while (true) {
                var siblings = allMessages.filter { it.id !in visited && it.parentId == cursor }
                    .sortedBy { it.timestamp }

                if (siblings.isEmpty()) {
                    // Dead end via the normal parentId chain. Before giving up, check for
                    // an unvisited message whose declared parentId doesn't resolve to ANY
                    // message in this conversation at all (e.g. a data bug left it pointing
                    // at a message from a different conversation). If found, splice the
                    // earliest such orphan in as a continuation of the current path instead
                    // of silently dropping it — and everything hanging off it — from view.
                    //
                    // SAFETY: only consider orphans that don't themselves belong to a
                    // different conversation (the cross-conversation leakage bug can place a
                    // foreign MODEL reply's row in a query result, but its parentId still
                    // names its real owner — which is NOT this conversation). Splicing such
                    // a foreign row in here is what let leaked messages hijack the visible
                    // path. Skip them: they will be cleaned up at their real owner.
                    val orphan = allMessages
                        .filter { it.id !in visited && it.parentId != null && it.parentId !in allIds }
                        .minByOrNull { it.timestamp }
                    if (orphan == null) break
                    siblings = listOf(orphan)
                }

                val selectedId = selectedChildren[cursor]
                val visibleSiblings = siblings.filter {
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
                }
                var selected = if (visibleSiblings.isNotEmpty()) {
                    visibleSiblings.find { it.id == selectedId } ?: visibleSiblings.last()
                } else {
                    siblings.find { it.id == selectedId } ?: siblings.last()
                }
                // Conversation root must be a USER message. When MODEL replies have been
                // orphaned onto parentId=null by the cross-conversation leakage bug (a MODEL
                // persisted with parentId=null, or reparented by compression), they would
                // otherwise become root-level siblings of the real USER root and win the
                // `visibleSiblings.last()` pick by timestamp — hiding the user's own message.
                // Force the root to USER when one is present; let MODEL/MODEL orphan chains
                // be reached only as siblings/branches, never as the entry point.
                if (cursor == null && selected.participant != Participant.USER) {
                    val userRoot = visibleSiblings.firstOrNull { it.participant == Participant.USER }
                    if (userRoot != null) selected = userRoot
                }
                DebugLog.d("MsgDisappear", "walk cursor=${cursor?.take(12) ?: "null"} " +
                    "siblings=${siblings.size} visible=${visibleSiblings.size} " +
                    "selectedId=${selectedId?.take(12) ?: "null"} -> picked=${selected.id.take(12)}(${selected.participant})")
                // Substitute streaming message if it matches
                if (streamingMsg != null && selected.id == streamingMsg.id) {
                    selected = streamingMsg
                }
                val isSynthetic = selected.id.startsWith(Constants.TOOL_MSG_PREFIX) ||
                    selected.id.startsWith(Constants.RESULT_MSG_PREFIX)
                if (!isSynthetic || (streamingMsg != null && selected.id == streamingMsg.id)) {
                    path.add(selected)
                }
                visited.add(selected.id)
                cursor = selected.id
            }
            // Append streaming message if not yet in path
            if (streamingMsg != null && path.none { it.id == streamingMsg.id }) {
                val lastId = path.lastOrNull()?.id
                if (streamingMsg.parentId == lastId || (streamingMsg.parentId == null && path.isEmpty())) {
                    path.add(streamingMsg)
                }
            }
            if (visibleTotal > 2 && path.size < visibleTotal / 2) {
                DebugLog.w("ResolvePath", "COLLAPSE: path=${path.size} visibleTotal=$visibleTotal " +
                    "allTotal=${allMessages.size} selected=${selectedChildren.size} | " +
                    "root sel=${selectedChildren[null]?.take(20)} | " +
                    "ids=" + allMessages.joinToString(",") { it.id.take(12) })
            }
            DebugLog.d("MsgDisappear", "resolvePath END: path=${path.size} " +
                "pathIds=${path.joinToString(",") { it.id.take(12) }}")
            return path
        }
    }
}