package com.orangeisland.app.api.ollama

import com.orangeisland.app.api.*

import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.api.util.StreamingThinkTagParser
import com.orangeisland.app.api.util.buildToolCallId
import com.orangeisland.app.api.util.prepareMessages
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

@Serializable
internal data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true,
    val options: JsonObject? = null,
    val tools: List<ToolDefinition>? = null
)

@Serializable
internal data class OllamaMessage(
    val role: String,
    val content: String = "",
    val thinking: String? = null,
    val images: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null
)

@Serializable
internal data class OllamaStreamResponse(
    val model: String? = null,
    val message: OllamaMessage? = null,
    val done: Boolean,
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("eval_count") val evalCount: Int? = null
)

@Serializable
internal data class OllamaTagsResponse(
    val models: List<OllamaModelInfo>
)

@Serializable
internal data class OllamaModelInfo(
    val name: String
)

class OllamaProvider : LlmProvider {
    override val name: String = Constants.PROVIDER_OLLAMA
    override val defaultBaseUrl: String = ""
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    override fun generateResponse(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Flow<StreamEvent> = flow {
        val baseUrl = config.baseUrl?.trimEnd('/')
            ?: defaultBaseUrl.ifEmpty { null }
            ?: return@flow emit(StreamEvent.Error(GenerationError.Configuration("Ollama base URL not configured")))
        val modelName = config.modelId

        val validatedPath = prepareMessages(messages, config.maxContextWindow)

        val apiMessages = mutableListOf<OllamaMessage>()
        if (!config.systemPrompt.isNullOrBlank()) {
            apiMessages.add(OllamaMessage(role = "system", content = config.systemPrompt))
        }


        apiMessages.addAll(validatedPath.flatMap { msg ->
            val entries = mutableListOf<OllamaMessage>()

            // tool_ messages: assistant turn with tool_calls (and thinking from segments)
            if (msg.id.startsWith(Constants.TOOL_MSG_PREFIX)) {
                val toolSegs = msg.segments?.filter { it.type == "tool" }
                val thinkingContent = msg.segments?.lastOrNull { it.type == "thought" }?.content
                if (!toolSegs.isNullOrEmpty()) {
                    val toolCalls = toolSegs.map { seg ->
                        val tid = seg.toolCallId ?: buildToolCallId(seg.toolName ?: "", seg.toolArgs ?: "{}")
                        val argsObj = try { json.parseToJsonElement(seg.toolArgs ?: "{}") as? JsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
                        OpenAiToolCall(
                            id = tid,
                            type = "function",
                            function = OpenAiFunctionCall(name = seg.toolName ?: "", arguments = argsObj ?: JsonObject(emptyMap()))
                        )
                    }
                    entries.add(OllamaMessage(
                        role = "assistant",
                        content = " ",
                        thinking = thinkingContent?.ifEmpty { null },
                        toolCalls = toolCalls
                    ))
                } else if (msg.toolCall != null) {
                    val toolId = msg.toolCall!!.toolCallId ?: buildToolCallId(msg.toolCall!!.toolName, msg.toolCall!!.arguments)
                    val argsObj = try { json.parseToJsonElement(msg.toolCall!!.arguments) as? JsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
                    entries.add(OllamaMessage(
                        role = "assistant",
                        content = " ",
                        thinking = thinkingContent?.ifEmpty { null },
                        toolCalls = listOf(OpenAiToolCall(
                            id = toolId,
                            type = "function",
                            function = OpenAiFunctionCall(name = msg.toolCall!!.toolName, arguments = argsObj ?: JsonObject(emptyMap()))
                        ))
                    ))
                }
                return@flatMap entries
            }

            // result_ messages carry the tool result(s)
            if (msg.id.startsWith(Constants.RESULT_MSG_PREFIX)) {
                val toolSegs = msg.segments?.filter { it.type == "tool" }
                if (!toolSegs.isNullOrEmpty()) {
                    for (seg in toolSegs) {
                        entries.add(OllamaMessage(
                            role = "user",
                            content = seg.toolResult ?: ""
                        ))
                    }
                } else if (msg.toolCall != null) {
                    entries.add(OllamaMessage(
                        role = "user",
                        content = msg.toolCall!!.result
                    ))
                }
                return@flatMap entries
            }

            val images = if (config.includeImages && msg.participant == Participant.USER) msg.images.mapNotNull { imagePath ->
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                    } else null
                } catch (e: Exception) { null }
            } else null

            // Normal message: text + images only
            entries.add(OllamaMessage(
                role = if (msg.participant == Participant.USER) "user" else "assistant",
                content = msg.text,
                images = images?.takeIf { it.isNotEmpty() }
            ))
            entries
        })

        val requestBody = OllamaChatRequest(
            model = config.modelId,
            messages = apiMessages,
            stream = true,
            tools = config.tools
        )

        try {
            val url = "$baseUrl/api/chat"
            val headers = mutableMapOf("Content-Type" to "application/json")
            if (config.apiKey.isNotEmpty()) {
                headers["Authorization"] = "Bearer ${config.apiKey}"
            }
            val requestBodyJson = json.encodeToString(OllamaChatRequest.serializer(), requestBody)
            DebugLog.d("OrangeIslandAPI", "[Ollama] REQ → $baseUrl/api/chat | model=${config.modelId} | msgs=${apiMessages.size} | tools=${config.tools?.size ?: 0}")
            DebugLog.d("OrangeIslandAPI", "[Ollama] BODY: ${requestBodyJson.take(4000)}")
            val maxAttempts = 3
            val retryableCodes = setOf(401, 429, 502, 503, 504)
            var attempt = 0
            var done = false

            while (attempt < maxAttempts && !done) {
                attempt++
                val handle = HttpClient.streamPost(url, requestBodyJson, headers)
                try {
                if (handle.code == 200) {
                    done = true
                    var line: String? = null
                    val thinkParser = StreamingThinkTagParser()
                    var receivedStructuredThinking = false

                    while (currentCoroutineContext().isActive) {
                        try {
                            line = handle.readLine()
                            if (line == null) break
                        } catch (e: java.net.SocketTimeoutException) {
                            if (!currentCoroutineContext().isActive) break
                            continue
                        }
                        try {
                            val response = json.decodeFromString<OllamaStreamResponse>(line)
                            response.message?.let { msg ->
                                // 1. Handle explicit thinking field (Ollama 0.5.4+)
                                msg.thinking?.let { thinking ->
                                    if (thinking.isNotEmpty() && config.thinkingEnabled) {
                                        emit(StreamEvent.ThoughtChunk(thinking, null))
                                        receivedStructuredThinking = true
                                    }
                                }

                                // 2. Handle tool calls
                                msg.toolCalls?.let { toolCalls ->
                                    val calls = toolCalls.mapNotNull { tc ->
                                        val id = tc.id ?: "${Constants.TOOL_CALL_ID_PREFIX}0"
                                        val name = tc.function?.name ?: ""
                                        val args = tc.function?.arguments?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() } ?: ""
                                        if (name.isNotEmpty()) StreamEvent.ToolCallRequest(id, name, args) else null
                                    }
                                    if (calls.size == 1) emit(calls.first())
                                    else if (calls.size > 1) emit(StreamEvent.ToolCallsRequest(calls))
                                }

                                // 3. Handle content: if structured thinking was received, emit content
                                // directly (any <think> tags in content are literal, not semantic).
                                // Otherwise, parse <think> tags as a fallback for older models.
                                if (msg.content.isNotEmpty()) {
                                    if (receivedStructuredThinking) {
                                        emit(StreamEvent.TextChunk(msg.content))
                                    } else {
                                        thinkParser.feed(
                                            content = msg.content,
                                            thinkingEnabled = config.thinkingEnabled,
                                            onText = { emit(StreamEvent.TextChunk(it)) },
                                            onThought = { emit(StreamEvent.ThoughtChunk(it)) }
                                        )
                                    }
                                }
                            }
                            if (response.done) {
                                thinkParser.flush(
                                    onText = { emit(StreamEvent.TextChunk(it)) },
                                    onThought = { emit(StreamEvent.ThoughtChunk(it)) }
                                )
                                val total = (response.promptEvalCount ?: 0) + (response.evalCount ?: 0)
                                emit(StreamEvent.UsageUpdate(total))
                            }
                        } catch (e: Exception) {
                            DebugLog.e("OrangeIslandAPI", "Parse error: ${e.message}")
                        }
                    }
                    if (!currentCoroutineContext().isActive) {
                        throw kotlinx.coroutines.CancellationException("Stream cancelled")
                    }
                } else {
                    val errorRaw = handle.errorBody ?: "Unknown error"
                    DebugLog.e("OrangeIslandAPI", "[Ollama] ERR ${handle.code}: $errorRaw")

                    if (handle.code in retryableCodes && attempt < maxAttempts) {
                        DebugLog.w("OrangeIslandAPI", "[Ollama] Transient error ${handle.code} on attempt $attempt/$maxAttempts, retrying in ${1000 * attempt}ms...")
                        emit(StreamEvent.Retrying(attempt, maxAttempts))
                        delay(1000L * attempt)
                    } else {
                        val genError = try {
                            val errorJson = json.decodeFromString<OpenAiErrorResponse>(errorRaw)
                            GenerationError.Api(code = errorJson.error.code ?: handle.code.toString(), type = errorJson.error.type, message = errorJson.error.message)
                        } catch (_: Exception) {
                            GenerationError.Network(statusCode = handle.code, message = errorRaw)
                        }
                        emit(StreamEvent.Error(genError))
                    }
                }
                } finally { handle.close() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            emit(StreamEvent.Error(GenerationError.Timeout))
        } catch (e: java.net.ConnectException) {
            emit(StreamEvent.Error(GenerationError.Network(statusCode = 0, message = e.localizedMessage ?: "Connection refused")))
        } catch (e: java.net.UnknownHostException) {
            emit(StreamEvent.Error(GenerationError.Network(statusCode = 0, message = e.localizedMessage ?: "Unknown host")))
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                emit(StreamEvent.Error(GenerationError.Unknown(e)))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val effectiveBaseUrl = baseUrl?.trimEnd('/') ?: "http://localhost:11434"
            val responseText = HttpClient.fetchModels("$effectiveBaseUrl/api/tags") ?: run {
                DebugLog.e("OrangeIslandAPI", "Failed to fetch Ollama models: empty response")
                return@withContext emptyList()
            }
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<OllamaTagsResponse>(responseText).models.map { it.name }
        } catch (e: Exception) {
            DebugLog.e("OrangeIslandAPI", "Ollama fetch failed: ${e.message}", e)
            emptyList()
        }
    }
}
