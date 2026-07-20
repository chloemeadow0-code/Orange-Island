package com.orangeisland.app.viewmodel

import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationManagerUserTemplateTest {
    @Test
    fun applyUserTemplateToMessages_wrapsOnlyNormalUserMessages() {
        val messages = listOf(
            ChatMessage(id = "u1", text = "hello", participant = Participant.USER),
            ChatMessage(id = Constants.RESULT_MSG_PREFIX + "r1", text = "tool output", participant = Participant.USER),
            ChatMessage(id = Constants.TOOL_MSG_PREFIX + "t1", text = "", participant = Participant.MODEL),
            ChatMessage(id = "m1", text = "assistant", participant = Participant.MODEL)
        )

        val result = applyUserTemplateToMessages(messages, "<wrap>", "</wrap>")

        assertEquals("<wrap>hello</wrap>", result[0].text)
        assertEquals("tool output", result[1].text)
        assertEquals("", result[2].text)
        assertEquals("assistant", result[3].text)
    }
}
