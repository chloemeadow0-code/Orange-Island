package com.orangeisland.app.util

import android.content.Context
import com.orangeisland.app.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class SearchResultFormatterTest {

    private fun mockGetString(resId: Int, vararg args: Any?): String = when (resId) {
        R.string.search_no_query -> "No query provided"
        R.string.search_no_results -> "No results found"
        R.string.search_no_response -> "No response received"
        R.string.unknown -> "Unknown"
        R.string.search_untitled -> "Untitled"
        R.string.shell_no_devices -> "No shell devices configured"
        R.string.conversation_list_empty -> "No conversations found"
        R.string.conversation_list_more -> "More conversations available"
        R.string.conversation_read_empty -> "No messages"
        R.string.conversation_read_more -> "More messages available"
        R.string.provider_no_keys -> "No API keys configured for Brave Search"
        R.string.search_error_format -> "Error: ${args.getOrNull(0) ?: "unknown"}"
        R.string.search_found_results -> "Found ${args[0]} results for '${args[1]}'"
        R.string.search_found_results_no_query -> "Found ${args[0]} results"
        R.string.search_no_matches -> "No matches for '${args[0]}'"
        R.string.search_role_user -> "User"
        R.string.search_role_model -> "Assistant"
        R.string.search_found_matches -> "Found ${args[0]} matches for '${args[1]}'"
        R.string.shell_result_server -> "Server: ${args[0]}"
        R.string.shell_result_command -> "Command: ${args[0]}"
        R.string.shell_result_exit_code -> "Exit code: ${args[0]}"
        R.string.shell_result_error -> "Error: ${args[0]}"
        R.string.conversation_list_header -> "Total: ${args[0]} (showing ${args[1]}-${args[2]})"
        R.string.conversation_read_header -> "Conversation: ${args[0]} (${args[1]} messages)"
        R.string.conversation_read_page -> "Page: ${args[0]}-${args[1]}"
        else -> "mocked_string_$resId"
    }

    private val context = mockk<Context> {
        every { getString(any<Int>()) } answers { mockGetString(firstArg()) }
        every { getString(any<Int>(), *anyVararg()) } answers {
            mockGetString(firstArg(), *secondArg())
        }
    }

    @Test
    fun isRawSearchResult_webSearch() {
        assertTrue(SearchResultFormatter.isRawSearchResult("""{"type":"web_search"}"""))
    }

    @Test
    fun isRawSearchResult_searchConversations() {
        assertTrue(SearchResultFormatter.isRawSearchResult("""{"type":"search_conversations"}"""))
    }

    @Test
    fun isRawSearchResult_conversationList() {
        assertTrue(SearchResultFormatter.isRawSearchResult("""{"type":"list_conversations"}"""))
    }

    @Test
    fun isRawSearchResult_plainText() {
        assertFalse(SearchResultFormatter.isRawSearchResult("Hello, world!"))
    }

    @Test
    fun isRawSearchResult_invalidJson() {
        assertFalse(SearchResultFormatter.isRawSearchResult("{invalid"))
    }

    @Test
    fun format_webSearch_validResults() {
        val json = """{"type":"web_search","query":"test","results":[{"title":"Title1","url":"https://a.com","description":"Desc1"},{"title":"Title2","url":"https://b.com","description":"Desc2"}]}"""
        val result = SearchResultFormatter.format(json, context)
        assertTrue(result.contains("Found 2 results"))
        assertTrue(result.contains("Title1"))
        assertTrue(result.contains("Title2"))
    }

    @Test
    fun format_webSearch_emptyResults() {
        val json = """{"type":"web_search","query":"test","results":[]}"""
        val result = SearchResultFormatter.format(json, context)
        assertEquals("No results found", result)
    }

    @Test
    fun format_webSearch_errorState() {
        val json = """{"type":"web_search","query":"test","error":"no_results"}"""
        val result = SearchResultFormatter.format(json, context)
        assertEquals("No results found", result)
    }

    @Test
    fun format_plainText_passthrough() {
        val text = "Just a regular message"
        val result = SearchResultFormatter.format(text, context)
        assertEquals(text, result)
    }

    @Test
    fun format_listShells_valid() {
        val json = """{"type":"list_shells","devices":[{"name":"dev1","description":"Test server"}]}"""
        val result = SearchResultFormatter.format(json, context)
        assertTrue(result.contains("dev1"))
        assertTrue(result.contains("Test server"))
    }
}
