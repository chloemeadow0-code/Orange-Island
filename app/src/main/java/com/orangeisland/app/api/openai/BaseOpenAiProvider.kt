package com.orangeisland.app.api.openai

import com.orangeisland.app.api.*

import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.api.util.StreamingThinkTagParser
import com.orangeisland.app.api.util.convertToOpenAiMessages
import com.orangeisland.app.api.util.prepareMessages
import com.orangeisland.app.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

abstract class BaseOpenAiProvider : LlmProvider {

    protected val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    // -- Override points --

    /**
     * Modify the outgoing request before serialization (e.g. add reasoning_effort, plugins).
     * The default implementation returns the request unchanged.
     */
    protected open fun customizeRequest(request: OpenAiChatRequest, config: ProviderConfig): OpenAiChatRequest = request

    /**
     * Extra HTTP headers to include in the POST to /chat/completions.
     */
    protected open fun getExtraHeaders(config: ProviderConfig): Map<String, String> = emptyMap()

    /**
     * Transform the system prompt before it is sent. Default: pass-through.
     */
    protected open fun transformSystemPrompt(prompt: String?): String? = prompt

    /**
     * Parse the delta from one SSE event and emit TextChunk / ThoughtChunk events.
     * The base class handles tool_calls accumulation, finish_reason emission, and usage
     * emission automatically.
     *
     * The default implementation covers the common OpenAI-compatible shape: a separate
     * `reasoning_content` field (gated on [ProviderConfig.thinkingEnabled]) plus `content`.
     * Providers with a different reasoning representation (e.g. OpenRouter's
     * `reasoning_details`) override this.
     */
    protected open suspend fun parseDeltaContent(
        delta: OpenAiDelta,
        config: ProviderConfig,
        thinkParser: StreamingThinkTagParser,
        emit: suspend (StreamEvent) -> Unit
    ) {
        delta.reasoningContent?.let { reasoning ->
            if (reasoning.isNotBlank() && config.thinkingEnabled) {
                emit(StreamEvent.ThoughtChunk(reasoning))
            }
        }
        delta.content?.let { content ->
            if (content.isNotEmpty()) {
                if (config.thinkingEnabled && delta.reasoningContent.isNullOrBlank()) {
                    thinkParser.feed(
                        content = content,
                        thinkingEnabled = config.thinkingEnabled,
                        onText = { emit(StreamEvent.TextChunk(it)) },
                        onThought = { emit(StreamEvent.ThoughtChunk(it)) }
                    )
                } else {
                    emit(StreamEvent.TextChunk(content))
                }
            }
        }
    }

    protected open val retryableStatusCodes: Set<Int> = setOf(429, 502, 503, 504)

    protected open val retryMissingV1BaseUrl: Boolean = false

    /** When false the `stream_options` field is omitted from the request body.
     *  Some OpenAI-compatible proxies/gateways (e.g. OpenCode) do not support
     *  `stream_options` and return "Upstream request failed" if it is present. */
    protected open val includeStreamOptions: Boolean = true

    protected open fun retryDelayMillis(statusCode: Int, attempt: Int): Long = 1000L * attempt

    /**
     * Hook for providers that support native video content. Given a list of messages
     * containing local/remote video references, return a copy where each reference is
     * resolved to a URL the provider accepts (data URL, mm_file://, http URL, etc.).
     * The default implementation encodes small local files as base64 data URLs.
     */
    protected open suspend fun resolveVideoUrls(
        messages: List<ChatMessage>,
        apiKey: String,
        baseUrl: String
    ): List<ChatMessage> {
        return messages.map { msg ->
            if (msg.videos.isEmpty()) return@map msg
            val resolved = msg.videos.mapNotNull { videoRef -> resolveVideoUrlDefault(videoRef) }
            msg.copy(videos = resolved)
        }
    }

    private fun resolveVideoUrlDefault(videoRef: String): String? {
        if (videoRef.startsWith("http://", ignoreCase = true) ||
            videoRef.startsWith("https://", ignoreCase = true) ||
            videoRef.startsWith("data:", ignoreCase = true) ||
            videoRef.startsWith("mm_file://", ignoreCase = true)
        ) {
            return videoRef
        }
        val file = java.io.File(videoRef.removePrefix("file://"))
        if (!file.exists()) {
            DebugLog.w("BaseOpenAiProvider", "Video file not found: $videoRef")
            return null
        }
        if (file.length() > com.orangeisland.app.util.Constants.MAX_INLINE_VIDEO_BYTES) {
            DebugLog.w("BaseOpenAiProvider", "Video too large to inline (${file.length()} bytes): $videoRef")
            return null
        }
        return try {
            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mime = guessVideoMimeType(file.name)
            "data:$mime;base64,$base64"
        } catch (e: Exception) {
            DebugLog.e("BaseOpenAiProvider", "Failed to encode video ${file.name}", e)
            null
        }
    }

    private fun guessVideoMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            fileName.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "video/mp4"
        }
    }

    // -- Template method --

    override fun generateResponse(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.baseUrl?.trimEnd('/') ?: defaultBaseUrl
        val endpointUrls = endpointCandidates(baseUrl, "chat/completions")

        val validatedMessages = prepareMessages(messages, config.maxContextWindow)
        val messagesWithVideos = resolveVideoUrls(validatedMessages, config.apiKey, baseUrl)

        val apiMessages = convertToOpenAiMessages(
            messages = messagesWithVideos,
            systemPrompt = transformSystemPrompt(config.systemPrompt),
            includeImages = config.includeImages,
            includeVideos = config.includeVideos,
            defaultVideoUrlOptions = config.videoUrlOptions
        )

        var request = OpenAiChatRequest(
            model = config.modelId,
            messages = apiMessages,
            stream = true,
            streamOptions = if (includeStreamOptions) OpenAiStreamOptions(includeUsage = true) else null,
            tools = config.tools,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
            topP = config.topP,
            frequencyPenalty = config.frequencyPenalty,
            presencePenalty = config.presencePenalty
        )
        request = customizeRequest(request, config)

        val thinkParser = StreamingThinkTagParser()

        try {
            val requestBodyJson = json.encodeToString(OpenAiChatRequest.serializer(), request)
            DebugLog.d("OrangeIslandAPI", "[$name] REQ -> ${endpointUrls.first()} | model=${config.modelId} | msgs=${apiMessages.size} | tools=${config.tools?.size ?: 0}")
            DebugLog.d("OrangeIslandAPI", "[$name] REQ_BODY -> ${requestBodyJson.take(4000)}")

            val headers = mutableMapOf("Content-Type" to "application/json")
            if (config.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${config.apiKey}"
            for ((key, value) in getExtraHeaders(config)) headers[key] = value

            val maxAttempts = 3
            var attempt = 0
            var finished = false

            while (attempt < maxAttempts && !finished) {
                attempt++
                var endpointIndex = 0
                var retryScheduled = false

                while (endpointIndex < endpointUrls.size && !finished && !retryScheduled) {
                    val endpointUrl = endpointUrls[endpointIndex]
                    val handle = HttpClient.streamPost(endpointUrl, requestBodyJson, headers, config.cancellationToken)
                    try {
                        if (handle.code == 200) {
                            consumeSuccessfulStream(handle, config, thinkParser) { emit(it) }
                            finished = true
                        } else {
                            val errorRaw = handle.errorBody ?: "Unknown error"
                            val hasV1Fallback = endpointIndex + 1 < endpointUrls.size
                            if (hasV1Fallback) {
                                DebugLog.w("OrangeIslandAPI", "[$name] ${handle.code} at $endpointUrl, retrying with ${endpointUrls[endpointIndex + 1]}")
                                endpointIndex++
                                continue
                            }

                            DebugLog.e("OrangeIslandAPI", "[$name] ERR ${handle.code} at $endpointUrl: ${errorRaw.take(4000)}")

                            if (handle.code in retryableStatusCodes && attempt < maxAttempts) {
                                val retryDelayMs = retryDelayMillis(handle.code, attempt)
                                DebugLog.w("OrangeIslandAPI", "[$name] Transient error ${handle.code} on attempt $attempt/$maxAttempts, retrying in ${retryDelayMs}ms...")
                                emit(StreamEvent.Retrying(attempt, maxAttempts))
                                delay(retryDelayMs)
                                retryScheduled = true
                            } else {
                                emit(StreamEvent.Error(buildGenerationError(handle.code, errorRaw, endpointUrls)))
                                finished = true
                            }
                        }
                    } finally {
                        handle.close()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            emit(StreamEvent.Error(GenerationError.Timeout))
        } catch (e: ConnectException) {
            emit(StreamEvent.Error(GenerationError.Network(statusCode = 0, message = e.localizedMessage ?: "Connection refused")))
        } catch (e: UnknownHostException) {
            emit(StreamEvent.Error(GenerationError.Network(statusCode = 0, message = e.localizedMessage ?: "Unknown host")))
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                emit(StreamEvent.Error(GenerationError.Unknown(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun consumeSuccessfulStream(
        handle: HttpClient.StreamHandle,
        config: ProviderConfig,
        thinkParser: StreamingThinkTagParser,
        emit: suspend (StreamEvent) -> Unit
    ) {
        val pendingToolCalls = mutableMapOf<Int, PendingToolCall>()

        while (currentCoroutineContext().isActive) {
            val line = try {
                handle.readLine()
            } catch (e: SocketTimeoutException) {
                if (!currentCoroutineContext().isActive) break
                continue
            } ?: break

            if (!line.startsWith("data: ")) continue
            val jsonStr = line.substring(6).trim()
            if (jsonStr == "[DONE]") break

            try {
                val response = json.decodeFromString<OpenAiStreamResponse>(jsonStr)
                val choice = response.choices?.firstOrNull()

                choice?.delta?.let { delta ->
                    parseDeltaContent(delta, config, thinkParser) { emit(it) }

                    delta.toolCalls?.forEach { tc ->
                        val existing = if (!tc.id.isNullOrBlank()) pendingToolCalls.values.firstOrNull { it.id == tc.id } else null
                        val pending = if (existing != null) existing else {
                            val idx = tc.index ?: pendingToolCalls.size
                            pendingToolCalls.getOrPut(idx) { PendingToolCall() }
                        }
                        if (!tc.id.isNullOrBlank()) pending.id = tc.id
                        tc.function?.name?.let { if (it.isNotEmpty()) pending.name = it }
                        tc.function?.arguments?.let {
                            pending.args.append(if (it is JsonPrimitive) it.content else it.toString())
                        }
                    }
                }

                if (choice?.finishReason == "tool_calls" && pendingToolCalls.isNotEmpty()) {
                    val calls = pendingToolCalls.values.filter { it.name.isNotEmpty() }.map {
                        StreamEvent.ToolCallRequest(it.id, it.name, it.args.toString())
                    }
                    pendingToolCalls.clear()
                    if (calls.size == 1) emit(calls.first())
                    else if (calls.size > 1) emit(StreamEvent.ToolCallsRequest(calls))
                }

                response.usage?.let { usage ->
                    emit(
                        StreamEvent.UsageUpdate(
                            tokenCount = usage.totalTokens,
                            thoughtsTokenCount = usage.completionTokensDetails?.reasoningTokens ?: 0,
                            cachedTokenCount = usage.promptTokensDetails?.cachedTokens ?: 0
                        )
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("OrangeIslandAPI", "Parse error: ${e.message}", e)
            }
        }

        thinkParser.flush(
            onText = { emit(StreamEvent.TextChunk(it)) },
            onThought = { emit(StreamEvent.ThoughtChunk(it)) }
        )

        if (!currentCoroutineContext().isActive) {
            throw CancellationException("Stream cancelled")
        }
    }

    private fun endpointCandidates(baseUrl: String, path: String): List<String> {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        val primary = "$normalizedBaseUrl/$cleanPath"
        if (!retryMissingV1BaseUrl || normalizedBaseUrl.isBlank() ||
            BaseUrlResolver.hasVersionSegment(normalizedBaseUrl)
        ) {
            return listOf(primary)
        }
        return listOf(primary, "$normalizedBaseUrl/v1/$cleanPath")
    }

    private fun buildGenerationError(
        statusCode: Int,
        errorRaw: String,
        endpointUrls: List<String>
    ): GenerationError {
        val endpointHint = if (statusCode == 404 && endpointUrls.size > 1) {
            "\nTried ${endpointUrls.joinToString(" and ")}. OpenAI-compatible servers often require a /v1 Base URL."
        } else {
            ""
        }
        return try {
            val errorJson = json.decodeFromString<OpenAiErrorResponse>(errorRaw)
            GenerationError.Api(
                code = errorJson.error.code ?: statusCode.toString(),
                type = errorJson.error.type,
                message = errorJson.error.message + endpointHint
            )
        } catch (_: Exception) {
            GenerationError.Network(statusCode = statusCode, message = errorRaw + endpointHint)
        }
    }

    private fun authHeaders(apiKey: String): Map<String, String> =
        if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")

    override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> = withContext(Dispatchers.IO) {
        try {
            val effectiveBaseUrl = baseUrl?.trimEnd('/') ?: defaultBaseUrl
            val endpointUrls = endpointCandidates(effectiveBaseUrl, "models")
            val headers = authHeaders(apiKey)
            var lastParseError: Exception? = null

            for ((index, endpointUrl) in endpointUrls.withIndex()) {
                val responseText = HttpClient.fetchModels(endpointUrl, headers)
                if (responseText == null) {
                    if (index < endpointUrls.lastIndex) {
                        DebugLog.w("OrangeIslandAPI", "Failed to fetch $name models from $endpointUrl; retrying ${endpointUrls[index + 1]}")
                    }
                    continue
                }

                try {
                    return@withContext json.decodeFromString<OpenAiModelListResponse>(responseText)
                        .data.map { it.id }.sorted()
                } catch (e: Exception) {
                    lastParseError = e
                    if (index < endpointUrls.lastIndex) {
                        DebugLog.w("OrangeIslandAPI", "Failed to parse $name models from $endpointUrl; retrying ${endpointUrls[index + 1]}")
                    }
                }
            }

            if (lastParseError != null) {
                DebugLog.e("OrangeIslandAPI", "Failed to parse $name models", lastParseError)
            } else {
                DebugLog.e("OrangeIslandAPI", "Failed to fetch $name models: empty response")
            }
            emptyList()
        } catch (e: Exception) {
            DebugLog.e("OrangeIslandAPI", "Failed to fetch $name models", e)
            emptyList()
        }
    }
}
