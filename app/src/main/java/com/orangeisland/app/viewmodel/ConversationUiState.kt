package com.orangeisland.app.viewmodel

import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog

/** 树 walk 所需的最小属性集。不暴露给外部，只在 resolvePath / selfHealOrphanBranches 内部使用。 */
internal interface TreeNode {
    val id: String
    val parentId: String?
    val timestamp: Long
    val participant: Participant
}

internal data class TreeNodeImpl(
    override val id: String,
    override val parentId: String?,
    override val timestamp: Long,
    override val participant: Participant
) : TreeNode

internal fun ChatMessage.toTreeNode(): TreeNode = TreeNodeImpl(id, parentId, timestamp, participant)
internal fun MessageEntity.toTreeNode(): TreeNode = TreeNodeImpl(id, parentId, timestamp, participant)

/** 给定某个 parent 下的 siblings（全部，非空），返回 resolvePath 会选哪个 child。
 *  1. 优先尊重 selectedId（用户手动切换的分支）
 *  2. 无 selectedId 时，在可见 siblings 中选 timestamp 最大（最新）
 *  3. 没有可见 siblings 时 fallback 到全部 siblings 选最新 */
internal fun selectChild(siblings: List<TreeNode>, selectedId: String?): TreeNode {
    val visibleSiblings = siblings.filter {
        !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
    }
    return if (visibleSiblings.isNotEmpty()) {
        visibleSiblings.find { it.id == selectedId } ?: visibleSiblings.last()
    } else {
        siblings.find { it.id == selectedId } ?: siblings.last()
    }
}

/** 给定根层的 siblings（全部，非空），返回 resolvePath 会选哪个根。
 *  1. 优先尊重 selectedId
 *  2. 无 selectedId 时选 timestamp 最大
 *  3. 若最大 sibling 不是 USER，强制纠正为 USER（当前层优先，无则仅在 siblings.size <= 1 时全局兜底最早 USER） */
internal fun selectRoot(
    siblings: List<TreeNode>,
    selectedId: String?,
    allNodes: List<TreeNode>
): TreeNode {
    val visibleSiblings = siblings.filter {
        !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
    }
    var selected = if (visibleSiblings.isNotEmpty()) {
        visibleSiblings.find { it.id == selectedId } ?: visibleSiblings.last()
    } else {
        siblings.find { it.id == selectedId } ?: siblings.last()
    }
    if (selected.participant != Participant.USER) {
        val userRoot = visibleSiblings.firstOrNull { it.participant == Participant.USER }
        if (userRoot != null) {
            selected = userRoot
        } else if (siblings.size <= 1) {
            val earliestUser = allNodes
                .filter { it.participant == Participant.USER &&
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                    !it.id.startsWith(Constants.RESULT_MSG_PREFIX) }
                .minByOrNull { it.timestamp }
            if (earliestUser != null) {
                selected = earliestUser
            }
        }
    }
    return selected
}

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
            val allById = allMessages.associateBy { it.id }
            val allIds = allById.keys
            val allNodes = allMessages.map { it.toTreeNode() }
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
            val preview = selectedChildren.entries.take(5).joinToString("; ") { (pid, cid) -> "${pid?.take(12) ?: "null"}->${cid.take(12)}" }
            DebugLog.d("MsgDisappear", "  selectedChildren count=${selectedChildren.size} preview=$preview")
            // Pre-build parent→children map and a timestamp-sorted list to avoid
            // repeated O(n) scans inside the while loop and orphan lookup.
            val parentMap = allMessages.groupBy { it.parentId }
            val sortedAll = allMessages.sortedBy { it.timestamp }
            var cursor: String? = null

            while (true) {
                var siblings = parentMap[cursor]?.filter { it.id !in visited }?.sortedBy { it.timestamp } ?: emptyList()

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
                    val orphan = sortedAll
                        .find { it.id !in visited && it.parentId != null && it.parentId !in allIds &&
                            it.timestamp >= tailTimestamp }
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
                // `.last()` pick by timestamp.
                if (cursor == null) {
                    DebugLog.w("MsgOrder", "ROOT siblings=${siblings.size} (ts-sorted): " +
                        siblings.joinToString(",") { "${it.id.take(12)}(${it.participant},ts=${it.timestamp})" } +
                        " | selectedChildren[null]=${selectedId?.take(20)}")
                }
                val selected = if (cursor == null) {
                    val selectedTree = selectRoot(siblings.map { it.toTreeNode() }, selectedId, allNodes)
                    allById[selectedTree.id]!!
                } else {
                    val selectedTree = selectChild(siblings.map { it.toTreeNode() }, selectedId)
                    allById[selectedTree.id]!!
                }
                // [MsgOrder] Record which message becomes the conversation ROOT (the first
                // thing rendered). If this is ever a MODEL message or a recently-sent USER
                // message that isn't the true first message, that's the "jumped to top" bug.
                if (cursor == null) {
                    DebugLog.w("MsgOrder", "ROOT PICKED=${selected.id.take(12)}(${selected.participant},ts=${selected.timestamp}) " +
                        "forcedToUser=${selected.participant == Participant.USER && visibleSiblings.firstOrNull { it.participant == Participant.USER }?.id == selected.id}")
                }
                if (allMessages.size < 50) {
                    DebugLog.d("MsgDisappear", "walk cursor=${cursor?.take(12) ?: "null"} " +
                        "siblings=${siblings.size} visible=${visibleSiblings.size} " +
                        "selectedId=${selectedId?.take(12) ?: "null"} -> picked=${selected.id.take(12)}(${selected.participant})")
                }
                // Substitute streaming message if it matches
                if (streamingMsg != null && selected.id == streamingMsg.id) {
                    path.add(streamingMsg)
                } else {
                    val isSynthetic = selected.id.startsWith(Constants.TOOL_MSG_PREFIX) ||
                        selected.id.startsWith(Constants.RESULT_MSG_PREFIX)
                    if (!isSynthetic) {
                        path.add(selected)
                    }
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
