package com.orangeisland.app.tool

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [McpToolProvider]'s name-mangling logic. These exercise the pure companion
 * helpers directly (no MCP SDK / network needed) — they are the regression guard for the
 * "400 Invalid tool use format" bug caused by MCP tool names that violate the
 * `^[a-zA-Z0-9_-]{1,64}$` regex enforced by OpenAI-compatible providers.
 */
class McpToolProviderNameTest {

    private val nameRegex = Regex("^[a-zA-Z0-9_-]{1,64}$")

    private fun allocateAll(vararg pairs: Pair<String, String>): List<String> {
        val used = mutableSetOf<String>()
        return pairs.map { (server, tool) ->
            McpToolProvider.allocateApiName(server, tool, used)
        }
    }

    @Test
    fun sanitizeName_collapsesRunsAndTrims() {
        assertEquals("foo_bar", McpToolProvider.sanitizeName("foo.bar"))
        assertEquals("foo_bar", McpToolProvider.sanitizeName("foo..bar"))
        assertEquals("foo_bar", McpToolProvider.sanitizeName("foo bar"))
        assertEquals("a_b_c", McpToolProvider.sanitizeName("a/b:c"))
        assertEquals("foo", McpToolProvider.sanitizeName(".foo."))
        // Single underscores are preserved; doubles collapse (keeps the __ separator unambiguous).
        assertEquals("read_file", McpToolProvider.sanitizeName("read_file"))
        assertEquals("read_file", McpToolProvider.sanitizeName("read__file"))
        // All-separator and empty inputs never produce an empty segment.
        assertEquals("x", McpToolProvider.sanitizeName("..."))
        assertEquals("x", McpToolProvider.sanitizeName(""))
    }

    @Test
    fun allocateApiName_producesProviderValidNames() {
        val names = allocateAll(
            "github" to "search/repositories",
            "fs" to "readFile",
            "weird server!" to "tool:with.dots",
        )
        names.forEach {
            assertTrue("'$it' must match provider function-name regex", nameRegex.matches(it))
            assertTrue("'$it' must keep the mcp__ prefix", it.startsWith(McpToolProvider.PREFIX))
        }
    }

    @Test
    fun allocateApiName_respects64CharCap() {
        val name = McpToolProvider.allocateApiName("server", "x".repeat(200), mutableSetOf())
        assertTrue("length=${name.length} must be <= 64", name.length <= 64)
        assertTrue(nameRegex.matches(name))
    }

    @Test
    fun allocateApiName_cappedEvenWithLongServerName() {
        // A huge server name must not push the total past 64.
        val name = McpToolProvider.allocateApiName("s".repeat(300), "tool", mutableSetOf())
        assertTrue("length=${name.length} must be <= 64", name.length <= 64)
        assertTrue(nameRegex.matches(name))
    }

    @Test
    fun allocateApiName_dedupsCollisionsWithSuffix() {
        // foo.bar and foo_bar both sanitize to foo_bar -> the second gets a _2 suffix.
        val names = allocateAll("srv" to "foo.bar", "srv" to "foo_bar")
        assertEquals(2, names.size)
        assertNotEquals(names[0], names[1])
        assertEquals("mcp__srv__foo_bar", names[0])
        assertEquals("mcp__srv__foo_bar_2", names[1])
        names.forEach { assertTrue(nameRegex.matches(it)) }
    }

    @Test
    fun allocateApiName_separatorStaysUnambiguous() {
        // The "__" separator must be the ONLY "__" in the name so parsePrefixedName is sound.
        val name = McpToolProvider.allocateApiName("my..server", "a..b", mutableSetOf())
        val rest = name.removePrefix(McpToolProvider.PREFIX)
        val sepIdx = rest.indexOf("__")
        assertTrue("separator must exist", sepIdx > 0)
        val server = rest.substring(0, sepIdx)
        val tool = rest.substring(sepIdx + 2)
        assertFalse("server segment '$server' must not contain __", server.contains("__"))
        assertFalse("tool segment '$tool' must not contain __", tool.contains("__"))
    }
}
