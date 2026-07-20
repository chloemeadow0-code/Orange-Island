package com.orangeisland.app.tool

import com.orangeisland.app.viewmodel.GenerationContext
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class WebSearchToolProviderTest {
    private val provider = WebSearchToolProvider()
    private val enabledCtx = GenerationContext(webSearchEnabled = true)
    private val disabledCtx = GenerationContext(webSearchEnabled = false)

    @Test
    fun definitions_whenEnabled_returnsTwoTools() {
        val defs = provider.definitions(enabledCtx)
        assertEquals(2, defs.size)
        assertEquals("web_search", defs[0].function.name)
        assertEquals("web_fetch", defs[1].function.name)
    }

    @Test
    fun definitions_whenDisabled_returnsEmpty() {
        assertTrue(provider.definitions(disabledCtx).isEmpty())
    }

    @Test
    fun handles_returnsTrueForWebTools() {
        assertTrue(provider.handles("web_search"))
        assertTrue(provider.handles("web_fetch"))
        assertFalse(provider.handles("unknown"))
    }
}

class RagToolProviderTest {
    private val provider = RagToolProvider(mockk(relaxed = true))
    private val enabledCtx = GenerationContext(accessPastConversations = true)
    private val disabledCtx = GenerationContext(accessPastConversations = false)

    @Test
    fun definitions_whenEnabled_returnsThreeTools() {
        val defs = provider.definitions(enabledCtx)
        assertEquals(3, defs.size)
        assertEquals("search_conversations", defs[0].function.name)
        assertEquals("list_conversations", defs[1].function.name)
        assertEquals("read_conversation", defs[2].function.name)
    }

    @Test
    fun definitions_whenDisabled_returnsEmpty() {
        assertTrue(provider.definitions(disabledCtx).isEmpty())
    }

    @Test
    fun handles_returnsTrueForConversationTools() {
        // RagToolProvider now owns execution of the conversation tools.
        assertTrue(provider.handles("search_conversations"))
        assertTrue(provider.handles("list_conversations"))
        assertTrue(provider.handles("read_conversation"))
        assertFalse(provider.handles("web_search"))
    }
}

class ShellToolProviderTest {
    private val provider = ShellToolProvider()
    private val emptyCtx = GenerationContext(shellEnabled = false)
    private val disabledCtx = GenerationContext(shellEnabled = true, shellDevices = emptyList())

    @Test
    fun definitions_whenDisabled_returnsEmpty() {
        assertTrue(provider.definitions(emptyCtx).isEmpty())
        assertTrue(provider.definitions(disabledCtx).isEmpty())
    }

    @Test
    fun definitions_whenSingleDevice_shellAndFileTools() {
        val ctx = GenerationContext(
            shellEnabled = true,
            shellDevices = listOf(
                com.orangeisland.app.data.ShellDeviceConfig(name = "server1", serverUrl = "http://localhost")
            )
        )
        val defs = provider.definitions(ctx)
        assertEquals(7, defs.size)
        val names = defs.map { it.function.name }.toSet()
        assertEquals(setOf("list_shells", "execute_shell_command", "file_read", "file_write", "file_edit", "file_glob", "file_grep"), names)
    }

    @Test
    fun definitions_whenMultiDevice_commandRequiresServer() {
        val ctx = GenerationContext(
            shellEnabled = true,
            shellDevices = listOf(
                com.orangeisland.app.data.ShellDeviceConfig(name = "s1", serverUrl = "http://a"),
                com.orangeisland.app.data.ShellDeviceConfig(name = "s2", serverUrl = "http://b")
            )
        )
        val defs = provider.definitions(ctx)
        val cmdTool = defs.find { it.function.name == "execute_shell_command" }
        assertNotNull(cmdTool)
        assertEquals(listOf("command", "server"), cmdTool!!.function.parameters.required)
    }

    @Test
    fun handles_returnsTrueForShellAndFileTools() {
        assertTrue(provider.handles("list_shells"))
        assertTrue(provider.handles("execute_shell_command"))
        assertTrue(provider.handles("file_read"))
        assertTrue(provider.handles("file_write"))
        assertTrue(provider.handles("file_edit"))
        assertTrue(provider.handles("file_glob"))
        assertTrue(provider.handles("file_grep"))
        assertFalse(provider.handles("unknown"))
    }
}
