package com.orangeisland.app.tool

import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.viewmodel.GenerationContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryToolProviderTest {

    private val memoryManager = mockk<MemoryManager> {
        every { listFiles() } returns listOf(
            MemoryManager.MemoryFileInfo("notes.md", "My notes"),
            MemoryManager.MemoryFileInfo("data.json", "JSON data")
        )
        coEvery { readFile(any()) } returns "file content"
        coEvery { createFile(any(), any(), any()) } returns "Created"
        coEvery { editFile(any(), any(), any(), any(), any(), any()) } returns "Edited"
        coEvery { deleteFile(any()) } returns "Deleted"
        coEvery { updateActiveMemory(any(), any(), any(), any()) } returns "Updated"
    }

    private val provider = MemoryToolProvider(memoryManager)

    private val ctx = GenerationContext(
        accessSavedMemories = true,
        accessActiveMemory = true
    )

    @Test
    fun definitions_whenEnabled_returnsSixMemoryTools() {
        val defs = provider.definitions(ctx)
        assertEquals(6, defs.size)
        val names = defs.map { it.function.name }.toSet()
        assertTrue(names.contains("list_memory_files"))
        assertTrue(names.contains("read_memory_file"))
        assertTrue(names.contains("create_memory_file"))
        assertTrue(names.contains("edit_memory_file"))
        assertTrue(names.contains("delete_memory_file"))
        assertTrue(names.contains("update_active_memory"))
    }

    @Test
    fun definitions_whenMemoryDisabled_returnsOnlyActiveMemoryTool() {
        val disabledCtx = ctx.copy(accessSavedMemories = false, accessActiveMemory = true)
        val defs = provider.definitions(disabledCtx)
        assertEquals(1, defs.size)
        assertEquals("update_active_memory", defs[0].function.name)
    }

    @Test
    fun definitions_whenAllDisabled_returnsEmpty() {
        val disabledCtx = ctx.copy(accessSavedMemories = false, accessActiveMemory = false)
        val defs = provider.definitions(disabledCtx)
        assertTrue(defs.isEmpty())
    }

    @Test
    fun handles_returnsTrueForMemoryTools() {
        assertTrue(provider.handles("list_memory_files"))
        assertTrue(provider.handles("read_memory_file"))
        assertTrue(provider.handles("update_active_memory"))
        assertFalse(provider.handles("web_search"))
        assertFalse(provider.handles("unknown_tool"))
    }

    @Test
    fun execute_listMemoryFiles_returnsJson() = runTest {
        val result = provider.execute("list_memory_files", "{}", ctx)
        assertTrue(result.contains("list_memory_files"))
        assertTrue(result.contains("notes.md"))
        assertTrue(result.contains("data.json"))
    }

    @Test
    fun execute_readMemoryFile_singleName() = runTest {
        val result = provider.execute("read_memory_file", """{"name":"notes.md"}""", ctx)
        assertEquals("file content", result)
    }

    @Test
    fun execute_createMemoryFile() = runTest {
        val result = provider.execute("create_memory_file", """{"name":"new.md","content":"hello"}""", ctx)
        assertEquals("Created", result)
    }

    @Test
    fun execute_updateActiveMemory_patch() = runTest {
        val result = provider.execute(
            "update_active_memory",
            """{"content":"placeholder","mode":"patch","old_string":"foo","new_string":"bar"}""",
            ctx
        )
        assertEquals("Updated", result)
        verify { memoryManager.updateActiveMemory("placeholder", "patch", "foo", "bar") }
    }

    @Test
    fun execute_unknownTool() = runTest {
        val result = provider.execute("unknown", "{}", ctx)
        assertTrue(result.contains("Unknown tool"))
    }
}
