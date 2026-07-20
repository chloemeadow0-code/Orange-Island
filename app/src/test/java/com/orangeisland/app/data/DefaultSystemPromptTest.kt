package com.orangeisland.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DefaultSystemPromptTest {
    @Test
    fun titleForLocale_usesChineseDefaultForChineseLocale() {
        assertEquals("Default", DefaultSystemPrompt.titleForLocale(Locale.ENGLISH))
        assertEquals("\u9ed8\u8ba4", DefaultSystemPrompt.titleForLocale(Locale.SIMPLIFIED_CHINESE))
        assertEquals("\u9810\u8a2d", DefaultSystemPrompt.titleForLocale(Locale.forLanguageTag("zh-Hant")))
        assertEquals("Predeterminado", DefaultSystemPrompt.titleForLocale(Locale.forLanguageTag("es")))
        assertEquals("Par d\u00e9faut", DefaultSystemPrompt.titleForLocale(Locale.FRENCH))
    }

    @Test
    fun create_includesRuntimeContextActiveMemoryAndToolPolicy() {
        val entry = DefaultSystemPrompt.create(Locale.ENGLISH)
        val systemPrompt = PredefinedVariables.compile(
            entry.systemItems,
            mapOf(
                PredefinedVariables.DATE to "2026-06-17",
                PredefinedVariables.TIME to "21:35:10",
                PredefinedVariables.ACTIVE_MEMORY to "User prefers concise answers."
            )
        )

        assertTrue(systemPrompt.contains("<current_date>2026-06-17</current_date>"))
        assertTrue(systemPrompt.contains("<current_time>21:35:10</current_time>"))
        assertTrue(systemPrompt.contains("<active_memory_context>\nUser prefers concise answers.\n</active_memory_context>"))
        assertTrue(systemPrompt.contains("Shell and device files:"))
        assertTrue(systemPrompt.contains("configured shell server or the Local Sandbox"))
        assertFalse(systemPrompt.contains("generate_image"))
    }

    @Test
    fun create_wrapsUserMessagesWithSentDateAndTimeMetadata() {
        val entry = DefaultSystemPrompt.create(Locale.ENGLISH)
        val prefix = PredefinedVariables.compile(
            entry.userPrependItems,
            mapOf(
                PredefinedVariables.SENT_DATE to "2026-06-17",
                PredefinedVariables.SENT_TIME to "21:35:10"
            ),
            emptyMap()
        )
        val suffix = PredefinedVariables.compile(entry.userPostpendItems, emptyMap(), emptyMap())

        assertEquals("<orangeisland_user_message sent_date=\"2026-06-17\" sent_time=\"21:35:10\">\n", prefix)
        assertEquals("\n</orangeisland_user_message>", suffix)
    }
}
