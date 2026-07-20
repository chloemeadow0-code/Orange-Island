package com.orangeisland.app.api

/**
 * Typed error hierarchy for LLM generation failures.
 *
 * Replaces ad-hoc string-based error messages in StreamEvent.Error with
 * structured types that enable differentiated UI handling (retry actions,
 * error icons, recovery strategies) per error category.
 *
 * Phase 1b creates the type hierarchy. Phase 7 migrates all provider
 * emit sites from StreamEvent.Error(String) to StreamEvent.Error(GenerationError).
 */
sealed class GenerationError {

    /** HTTP-level error (connection refused, timeout, DNS failure, etc.). */
    data class Network(
        val statusCode: Int,
        val message: String
    ) : GenerationError()

    /** API-level error returned by the provider (invalid key, rate limit, server error). */
    data class Api(
        val code: String?,
        val type: String?,
        val message: String
    ) : GenerationError()

    /** Failed to parse a line from the SSE stream. */
    data class SseParse(
        val rawLine: String,
        val cause: String
    ) : GenerationError()

    /** A tool execution failed (memory, web search, shell, RAG). */
    data class ToolExecution(
        val toolName: String,
        val arguments: String,
        val message: String
    ) : GenerationError()

    /** Image/video/PDF transcription failed. */
    data class Transcription(
        val imagePath: String,
        val message: String
    ) : GenerationError()

    /** Embedding computation failed. */
    data class Embedding(
        val modelId: String,
        val message: String
    ) : GenerationError()

    /** On-device GGUF model error (file not found, failed to load, etc.). */
    data class LocalModel(
        val message: String
    ) : GenerationError()

    /** Missing or invalid configuration (no API key, no base URL, etc.). */
    data class Configuration(
        val message: String
    ) : GenerationError()

    /** Wraps an unexpected exception. */
    data class Unknown(
        val cause: Throwable
    ) : GenerationError()

    /** Generation was cancelled by the user. */
    object Cancelled : GenerationError()

    /** Request timed out waiting for a server response. */
    object Timeout : GenerationError()

    /** Human-readable message suitable for displaying in the UI. */
    fun userMessage(): String = when (this) {
        is Network -> when (statusCode) {
            401 -> "Authentication failed. Please check your API key."
            429 -> "Rate limit exceeded. Please wait and try again."
            in 500..599 -> "Server error ($statusCode). The service may be temporarily unavailable."
            else -> "Network error ($statusCode): $message"
        }
        is Api -> buildString {
            if (code != null) append("$code")
            if (type != null) append(" [$type]")
            if (isNotEmpty()) append(": ")
            append(message)
        }
        is SseParse -> "Failed to parse server response."
        is ToolExecution -> "Tool '$toolName' failed: $message"
        is Transcription -> "Image transcription failed: $message"
        is Embedding -> "Embedding failed: $message"
        is LocalModel -> message
        is Configuration -> message
        is Unknown -> cause.localizedMessage ?: "An unexpected error occurred."
        Cancelled -> "Generation cancelled."
        Timeout -> "Request timed out."
    }
}
