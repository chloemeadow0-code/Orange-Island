package com.orangeisland.app.viewmodel

import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import org.junit.Assert.*
import org.junit.Test

class ConversationUiStateTest {

    private val now = System.currentTimeMillis()

    private fun msg(
        id: String, parentId: String? = null, text: String = "text",
        participant: Participant = Participant.USER
    ) = ChatMessage(
        id = id, parentId = parentId, text = text,
        participant = participant, timestamp = now + id.hashCode()
    )

    @Test
    fun emptyState_returnsEmptyPath() {
        val path = ConversationUiState.resolvePath(emptyList(), null, emptyMap())
        assertTrue(path.isEmpty())
    }

    @Test
    fun linearConversation_returnsAllMessages() {
        val msgs = listOf(
            msg("u1", null, "q1"),
            msg("m1", "u1", "a1", Participant.MODEL),
            msg("u2", "m1", "q2"),
            msg("m2", "u2", "a2", Participant.MODEL)
        )
        val path = ConversationUiState.resolvePath(msgs, null, emptyMap())
        assertEquals(4, path.size)
        assertEquals("u1", path[0].id)
        assertEquals("m2", path[3].id)
    }

    @Test
    fun branchSelection_followsSelectedChild() {
        val msgs = listOf(
            msg("u1", null, "q1"),
            msg("m1a", "u1", "a1a", Participant.MODEL), // first sibling
            msg("m1b", "u1", "a1b", Participant.MODEL)  // second sibling (regenerated)
        )
        // Select the first sibling
        val path = ConversationUiState.resolvePath(msgs, null, mapOf("u1" to "m1a"))
        assertEquals(2, path.size)
        assertEquals("m1a", path[1].id)
    }

    @Test
    fun branchSelection_defaultsToLast() {
        val msgs = listOf(
            msg("u1", null, "q1"),
            msg("m1a", "u1", "a1a", Participant.MODEL),
            msg("m1b", "u1", "a1b", Participant.MODEL)
        )
        // No selection → last sibling
        val path = ConversationUiState.resolvePath(msgs, null, emptyMap())
        assertEquals(2, path.size)
        assertEquals("m1b", path[1].id)
    }

    @Test
    fun syntheticToolMessages_filteredOut() {
        val msgs = listOf(
            msg("u1", null, "q1"),
            msg("m1", "u1", "a1", Participant.MODEL),
            msg(Constants.TOOL_MSG_PREFIX + "t1", "m1", "", Participant.MODEL),
            msg(Constants.RESULT_MSG_PREFIX + "r1", Constants.TOOL_MSG_PREFIX + "t1", "result", Participant.MODEL)
        )
        val path = ConversationUiState.resolvePath(msgs, null, emptyMap())
        assertEquals(2, path.size)
        assertEquals("u1", path[0].id)
        assertEquals("m1", path[1].id)
    }

    @Test
    fun streamingMessage_substitutesMatchingId() {
        val dbMsgs = listOf(
            msg("u1", null, "q1"),
            msg("m1", "u1", "streaming...", Participant.MODEL)
        )
        val streaming = ChatMessage(
            id = "m1", parentId = "u1", text = "updated stream text",
            participant = Participant.MODEL, status = MessageStatus.SENDING
        )
        val path = ConversationUiState.resolvePath(dbMsgs, streaming, emptyMap())
        assertEquals(2, path.size)
        assertEquals("updated stream text", path[1].text)
        assertEquals(MessageStatus.SENDING, path[1].status)
    }

    @Test
    fun streamingMessage_appendedIfNew() {
        val msgs = listOf(msg("u1", null, "q1"))
        val streaming = ChatMessage(
            id = "m1", parentId = "u1", text = "new response",
            participant = Participant.MODEL, status = MessageStatus.SENDING
        )
        val path = ConversationUiState.resolvePath(msgs, streaming, emptyMap())
        assertEquals(2, path.size)
        assertEquals("m1", path[1].id)
    }
}
