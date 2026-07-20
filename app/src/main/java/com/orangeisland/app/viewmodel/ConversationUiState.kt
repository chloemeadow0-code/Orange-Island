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
            var cursor: String? = null

            while (true) {
                val siblings = allMessages.filter { it.parentId == cursor }
                    .sortedBy { it.timestamp }
                if (siblings.isEmpty()) break

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
