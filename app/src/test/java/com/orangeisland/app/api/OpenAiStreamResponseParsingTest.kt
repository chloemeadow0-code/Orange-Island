package com.orangeisland.app.api

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for streaming chunks emitted by minimal OpenAI-compatible
 * proxies (e.g. liandkiwi.zeabur.app) that omit optional fields like "index"
 * or return a partial usage object. Such chunks must still parse instead of
 * being dropped with MissingFieldException (which made the whole reply empty
 * and surfaced as a generic "generation failed" in the UI).
 */
class OpenAiStreamResponseParsingTest {

    // Same configuration as BaseOpenAiProvider's json instance.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun `stream chunk without index field parses delta content`() {
        val chunk = """{"id":"chatcmpl-1","choices":[{"delta":{"role":"assistant","content":"你好"}}]}"""
        val response = json.decodeFromString<OpenAiStreamResponse>(chunk)

        val choice = response.choices?.firstOrNull()
        assertNotNull(choice)
        assertEquals(0, choice!!.index)
        assertEquals("你好", choice.delta?.content)
        assertNull(choice.finishReason)
    }

    @Test
    fun `stream chunk without index field parses reasoning content`() {
        val chunk = """{"choices":[{"delta":{"reasoning_content":"thinking..."}}]}"""
        val response = json.decodeFromString<OpenAiStreamResponse>(chunk)

        val choice = response.choices?.firstOrNull()
        assertNotNull(choice)
        assertEquals(0, choice!!.index)
        assertEquals("thinking...", choice.delta?.reasoningContent)
    }

    @Test
    fun `final chunk without index field parses finish_reason`() {
        val chunk = """{"choices":[{"delta":{},"finish_reason":"stop"}]}"""
        val response = json.decodeFromString<OpenAiStreamResponse>(chunk)

        val choice = response.choices?.firstOrNull()
        assertNotNull(choice)
        assertEquals("stop", choice!!.finishReason)
    }

    @Test
    fun `partial usage object with missing token fields parses with zero defaults`() {
        val chunk = """{"choices":[],"usage":{"total_tokens":42}}"""
        val response = json.decodeFromString<OpenAiStreamResponse>(chunk)

        val usage = response.usage
        assertNotNull(usage)
        assertEquals(42, usage!!.totalTokens)
        assertEquals(0, usage.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertNull(usage.completionTokensDetails)
        assertNull(usage.promptTokensDetails)
    }

    @Test
    fun `empty usage object parses with all zero defaults`() {
        val chunk = """{"choices":null,"usage":{}}"""
        val response = json.decodeFromString<OpenAiStreamResponse>(chunk)

        val usage = response.usage
        assertNotNull(usage)
        assertEquals(0, usage!!.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertEquals(0, usage.totalTokens)
    }

    @Test
    fun `standard chunk with index field still parses`() {
        val chunk = """{"id":"chatcmpl-1","choices":[{"index":0,"delta":{"content":"hi"},"finish_reason":null}],"usage":null}"""
        val response = json.decodeFromString<OpenAiStreamResponse>(chunk)

        val choice = response.choices?.firstOrNull()
        assertNotNull(choice)
        assertEquals(0, choice!!.index)
        assertEquals("hi", choice.delta?.content)
    }
}
