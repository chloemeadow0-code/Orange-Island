package com.orangeisland.app.api.util

import com.orangeisland.app.api.OpenAiContentPart
import com.orangeisland.app.api.OpenAiImageUrl
import com.orangeisland.app.api.OpenAiMessage
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import org.junit.Assert.*
import org.junit.Test

class MessageConverterTest {

    @Test
    fun buildToolCallId_deterministic() {
        val id1 = buildToolCallId("read_file", """{"path":"/test"}""")
        val id2 = buildToolCallId("read_file", """{"path":"/test"}""")
        assertEquals(id1, id2)
    }

    @Test
    fun buildToolCallId_prefixApplied() {
        val id = buildToolCallId("my_tool", "{}", "pre_")
        assertTrue(id.startsWith("pre_my_tool_"))
    }

    @Test
    fun limitContext_emptyList() {
        val result = limitContext(emptyList(), 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun limitContext_respectsMaxUserMessages() {
        val msgs = (1..10).map { i ->
            ChatMessage(
                id = "msg$i",
                parentId = null,
                text = "msg $i",
                participant = if (i % 2 == 0) Participant.MODEL else Participant.USER
            )
        }
        val result = limitContext(msgs, 3)
        val userCount = result.count { it.participant == Participant.USER }
        assertTrue(userCount <= 3)
    }

    @Test
    fun limitContext_includesToolMessages() {
        val msgs = listOf(
            ChatMessage(id = "u1", parentId = null, text = "user1", participant = Participant.USER),
            ChatMessage(id = "m1", parentId = "u1", text = "resp1", participant = Participant.MODEL),
            ChatMessage(id = Constants.TOOL_MSG_PREFIX + "t1", parentId = "m1", text = "",
                participant = Participant.MODEL),
            ChatMessage(id = Constants.RESULT_MSG_PREFIX + "r1", parentId = "t1", text = "result",
                participant = Participant.MODEL),
            ChatMessage(id = "m2", parentId = "r1", text = "resp2", participant = Participant.MODEL),
            ChatMessage(id = "u2", parentId = "m2", text = "user2", participant = Participant.USER),
            ChatMessage(id = "m3", parentId = "u2", text = "resp3", participant = Participant.MODEL)
        )
        // maxUserMessages=2: reversed iteration processes m3→u2→m2→r1→t1→m1→u1
        // m3 (non-tool, count=1), u2 (non-tool, count=2, break)
        // result = [u2, m3]
        val result = limitContext(msgs, 2)
        assertEquals(2, result.size)
        assertEquals("u2", result[0].id)
        assertEquals("m3", result[1].id)
    }

    @Test
    fun convertToOpenAiMessages_systemPrompt() {
        val msgs = listOf(
            ChatMessage(id = "u1", parentId = null, text = "hello", participant = Participant.USER)
        )
        val result = convertToOpenAiMessages(msgs, "You are helpful")
        assertEquals("system", result.first().role)
        assertEquals("You are helpful", result.first().content.first().text)
    }

    @Test
    fun convertToOpenAiMessages_userAndModelRoles() {
        val msg = ChatMessage(id = "u1", text = "hello", participant = Participant.USER)
        val result = convertToOpenAiMessages(listOf(msg))
        assertEquals("user", result.first().role)

        val modelMsg = ChatMessage(id = "m1", text = "response", participant = Participant.MODEL)
        val result2 = convertToOpenAiMessages(listOf(modelMsg))
        assertEquals("assistant", result2.first().role)
    }

    @Test
    fun convertToOpenAiMessages_includeImagesFalse() {
        val msg = ChatMessage(
            id = "u1", text = "look at this",
            images = listOf("/nonexistent/image.jpg"),
            participant = Participant.USER
        )
        val result = convertToOpenAiMessages(listOf(msg), includeImages = false)
        assertEquals(1, result.first().content.size) // only text, no image
        assertEquals("text", result.first().content.first().type)
    }

    @Test
    fun convertToOpenAiMessages_emptyText_addsSpacePart() {
        val msg = ChatMessage(id = "u1", text = "", participant = Participant.USER)
        val result = convertToOpenAiMessages(listOf(msg))
        assertEquals(1, result.first().content.size)
        assertEquals(" ", result.first().content.first().text)
    }

    @Test
    fun projectAssistantImagesToLatestUserMessage_movesLatestGeneratedImageContext() {
        val messages = listOf(
            ChatMessage(id = "u1", text = "draw this", participant = Participant.USER),
            ChatMessage(id = "m1", text = "done", images = listOf("old.jpg"), participant = Participant.MODEL),
            ChatMessage(id = "m2", text = "follow-up answer", participant = Participant.MODEL),
            ChatMessage(id = "u2", text = "what is in it?", images = listOf("user.jpg"), participant = Participant.USER)
        )

        val result = projectAssistantImagesToLatestUserMessage(messages, includeImages = true)

        assertTrue(result[1].images.isEmpty())
        assertEquals(listOf("old.jpg", "user.jpg"), result[3].images)
        assertTrue(result[3].text.contains("generated by the assistant"))
        assertTrue(result[3].text.endsWith("what is in it?"))
    }

    @Test
    fun projectAssistantImagesToLatestUserMessage_usesMostRecentGeneratedImageSet() {
        val messages = listOf(
            ChatMessage(id = "u1", text = "draw first", participant = Participant.USER),
            ChatMessage(id = "m1", text = "first", images = listOf("first.jpg"), participant = Participant.MODEL),
            ChatMessage(id = "u2", text = "draw second", participant = Participant.USER),
            ChatMessage(id = "m2", text = "second", images = listOf("second-a.jpg", "second-b.jpg"), participant = Participant.MODEL),
            ChatMessage(id = "u3", text = "compare details", participant = Participant.USER)
        )

        val result = projectAssistantImagesToLatestUserMessage(messages, includeImages = true)

        assertTrue(result[1].images.isEmpty())
        assertTrue(result[3].images.isEmpty())
        assertEquals(listOf("second-a.jpg", "second-b.jpg"), result[4].images)
        assertTrue(result[4].text.contains("first 2 attached images"))
    }

    @Test
    fun projectAssistantImagesToLatestUserMessage_includeImagesFalse_doesNotInjectContext() {
        val messages = listOf(
            ChatMessage(id = "u1", text = "draw this", participant = Participant.USER),
            ChatMessage(id = "m1", text = "done", images = listOf("generated.jpg"), participant = Participant.MODEL),
            ChatMessage(id = "u2", text = "continue", participant = Participant.USER)
        )

        val result = projectAssistantImagesToLatestUserMessage(messages, includeImages = false)

        assertTrue(result[1].images.isEmpty())
        assertTrue(result[2].images.isEmpty())
        assertEquals("continue", result[2].text)
    }
}
