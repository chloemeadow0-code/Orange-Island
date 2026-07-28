package com.orangeisland.app.viewmodel

import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants

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
            return path
        }
    }
}