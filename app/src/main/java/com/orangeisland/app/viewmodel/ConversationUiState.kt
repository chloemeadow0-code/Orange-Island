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
                    // at a message from a different conversation). If found, splice such an
                    // orphan in as a continuation of the current path instead of silently
                    // dropping it — and everything hanging off it — from view.
                    //
                    // ORDER SAFETY: only splice an orphan whose timestamp is NOT older than
                    // the path's current tail. Splicing an older orphan onto the tail would
                    // place an old message after newer ones (the "old messages jump after new
                    // ones"乱序). Older orphans are left for selfHealOrphanBranches to re-chain
                    // into their correct position; they are NOT appended here.
                    //
                    // SAFETY: only consider orphans that don't themselves belong to a
                    // different conversation (the cross-conversation leakage bug can place a
                    // foreign MODEL reply's row in a query result, but its parentId still
                    // names its real owner — which is NOT this conversation). Splicing such
                    // a foreign row in here is what let leaked messages hijack the visible
                    // path. Skip them: they will be cleaned up at their real owner.
                    val tailTimestamp = path.lastOrNull()?.timestamp ?: 0L
                    val orphan = allMessages
                        .filter { it.id !in visited && it.parentId != null && it.parentId !in allIds &&
                            it.timestamp >= tailTimestamp }
                        .minByOrNull { it.timestamp }
                    if (orphan == null) break
                    siblings = listOf(orphan)
                }

                val selectedId = selectedChildren[cursor]
                val visibleSiblings = siblings.filter {
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
                }
                // [MsgOrder] Always log the ROOT layer: which messages claim parentId=null,
                // their timestamps, and which one gets picked. This is the #1 place a message
                // can "jump to the top" — when it wrongly becomes a root sibling and wins the
                // `.last()` pick by timestamp. Logging every resolve lets us catch it even when
                // the user just opens a conversation (no send involved).
                if (cursor == null) {
                    DebugLog.w("MsgOrder", "ROOT siblings=${siblings.size} (ts-sorted): " +
                        siblings.joinToString(",") { "${it.id.take(12)}(${it.participant},ts=${it.timestamp})" } +
                        " | selectedChildren[null]=${selectedId?.take(20)}")
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
                    if (userRoot != null) {
                        selected = userRoot
                    } else if (siblings.size <= 1) {
                        // RECOVERY: the root layer has NO USER message at all — the
                        // conversation tree is corrupt (e.g. a prior compressHistory bug
                        // reparented a MODEL message to parentId=null, orphaning the real
                        // first USER message underneath it). Instead of rendering that stray
                        // MODEL at the top, jump the walk to the earliest USER message in the
                        // whole conversation by timestamp and use IT as the root. This keeps
                        // the chat readable for already-corrupted data; the root invariant is
                        // re-enforced for new data by compressHistory's MODEL-root guard.
                        val earliestUser = allMessages
                            .filter { it.participant == Participant.USER &&
                                !it.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                                !it.id.startsWith(Constants.RESULT_MSG_PREFIX) }
                            .minByOrNull { it.timestamp }
                        if (earliestUser != null) {
                            DebugLog.w("MsgOrder", "ROOT RECOVERY: root layer had no USER " +
                                "(picked=${selected.id.take(12)}(${selected.participant})); " +
                                "re-rooting to earliest USER ${earliestUser.id.take(12)} ts=${earliestUser.timestamp}")
                            selected = earliestUser
                        }
                    }
                }
                // [MsgOrder] Record which message becomes the conversation ROOT (the first
                // thing rendered). If this is ever a MODEL message or a recently-sent USER
                // message that isn't the true first message, that's the "jumped to top" bug.
                if (cursor == null) {
                    DebugLog.w("MsgOrder", "ROOT PICKED=${selected.id.take(12)}(${selected.participant},ts=${selected.timestamp}) " +
                        "forcedToUser=${selected.participant == Participant.USER && visibleSiblings.firstOrNull { it.participant == Participant.USER }?.id == selected.id}")
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