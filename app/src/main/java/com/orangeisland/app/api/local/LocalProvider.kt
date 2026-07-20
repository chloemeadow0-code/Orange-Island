package com.orangeisland.app.api.local

import com.orangeisland.app.api.*

import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.api.util.ThinkingParser
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.Participant
import com.orangeisland.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

class LocalProvider(
    private val context: Context,
    private val settings: SettingsRepository
) : LlmProvider {

    companion object {
        private const val TAG = "LocalProvider"
        private const val CONTEXT_EXCEEDED_PREFIX = "LOCAL_CONTEXT_EXCEEDED:"
    }

    override val name: String = Constants.PROVIDER_LOCAL
    override val defaultBaseUrl: String = ""

    private var currentEngine: LlamaChatEngine? = null
    private val engineLock = Mutex()

    override fun generateResponse(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Flow<StreamEvent> = flow {
        val chatModels = settings.localChatModels.first()
        val modelConfig = chatModels.find { it.modelId == config.modelId }
        if (modelConfig == null) {
            emit(StreamEvent.Error(GenerationError.LocalModel("Local model not found: ${config.modelId}")))
            return@flow
        }

        val engine = ensureEngineLoaded(modelConfig)
        if (engine == null) {
            emit(StreamEvent.Error(GenerationError.LocalModel("Failed to load model: ${modelConfig.alias}")))
            return@flow
        }

        // Build template messages, collecting images per-message with <__media__> markers
        val imagePaths = mutableListOf<String>()
        val templateMessages = buildTemplateMessages(messages, config.systemPrompt, imagePaths)
        val hasImages = imagePaths.isNotEmpty()

        // Try native chat template first, fall back to ChatML
        val prompt = engine.applyTemplate(templateMessages, addAss = true)
            ?: buildPrompt(templateMessages)
        if (hasImages) {
            DebugLog.d(TAG, "Generated multimodal prompt (${prompt.length} chars, ${imagePaths.size} images)")
        } else {
            DebugLog.d(TAG, "Generated prompt (${prompt.length} chars): ${prompt.take(200)}...")
        }

        // Generate tokens with unified thinking parsing
        var totalTokens = 0
        var stopped = false
        var rawBuf = ""
        val STOP_PATTERNS = listOf("<|im_end|>", "<|im_start|>")
        val thinkParser = ThinkingParser()
        try {
            val tokenFlow = if (hasImages) {
                engine.generateWithImages(
                    prompt = prompt,
                    imagePaths = imagePaths,
                    temperature = config.temperature ?: modelConfig.temperature,
                    topP = config.topP ?: modelConfig.topP,
                    maxTokens = config.maxTokens ?: modelConfig.maxTokens
                )
            } else {
                engine.generate(
                    prompt = prompt,
                    temperature = config.temperature ?: modelConfig.temperature,
                    topP = config.topP ?: modelConfig.topP,
                    maxTokens = config.maxTokens ?: modelConfig.maxTokens
                )
            }
            tokenFlow.collect { token ->
                if (!coroutineContext.isActive) {
                    engine.cancel()
                    return@collect
                }
                if (stopped) return@collect
                totalTokens++

                // Check for stop patterns in the rolling buffer
                rawBuf += token
                val hit = STOP_PATTERNS.firstOrNull { p -> rawBuf.contains(p) }
                if (hit != null) {
                    // Strip the stop pattern and anything after it, then stop
                    val cleanEnd = rawBuf.substringBefore(hit)
                    if (cleanEnd.isNotEmpty()) {
                        thinkParser.feed(
                            content = cleanEnd,
                            thinkingEnabled = config.thinkingEnabled,
                            onText = { emit(StreamEvent.TextChunk(it)) },
                            onThought = { emit(StreamEvent.ThoughtChunk(it)) }
                        )
                    }
                    engine.cancel()
                    stopped = true
                    return@collect
                }

                // Keep buffer bounded — only as much as longest stop pattern
                val maxPatLen = STOP_PATTERNS.maxOf { it.length }
                if (rawBuf.length > maxPatLen * 2) {
                    val emitPart = rawBuf.substring(0, rawBuf.length - maxPatLen)
                    thinkParser.feed(
                        content = emitPart,
                        thinkingEnabled = config.thinkingEnabled,
                        onText = { emit(StreamEvent.TextChunk(it)) },
                        onThought = { emit(StreamEvent.ThoughtChunk(it)) }
                    )
                    rawBuf = rawBuf.substring(rawBuf.length - maxPatLen)
                }
            }
            // Flush remaining buffer (no stop pattern found)
            if (!stopped && rawBuf.isNotEmpty()) {
                thinkParser.feed(
                    content = rawBuf,
                    thinkingEnabled = config.thinkingEnabled,
                    onText = { emit(StreamEvent.TextChunk(it)) },
                    onThought = { emit(StreamEvent.ThoughtChunk(it)) }
                )
            }
            thinkParser.flush(
                onText = { emit(StreamEvent.TextChunk(it)) },
                onThought = { emit(StreamEvent.ThoughtChunk(it)) }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            engine.cancel()
            emit(StreamEvent.Error(GenerationError.Cancelled))
            throw e
        } catch (e: Exception) {
            DebugLog.e(TAG, "Generation failed", e)
            emit(StreamEvent.Error(GenerationError.LocalModel(formatGenerationError(e, modelConfig))))
            return@flow
        }

        emit(StreamEvent.UsageUpdate(totalTokens))
    }.flowOn(Dispatchers.IO)

    private fun formatGenerationError(
        error: Exception,
        model: com.orangeisland.app.data.LocalChatModelConfig
    ): String {
        val message = error.message ?: "Unknown error"
        if (message.startsWith(CONTEXT_EXCEEDED_PREFIX)) {
            val parts = message.removePrefix(CONTEXT_EXCEEDED_PREFIX).split(":")
            val promptTokens = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val contextTokens = parts.getOrNull(1)?.toIntOrNull() ?: model.nCtx
            return context.getString(R.string.local_context_exceeded, promptTokens, contextTokens)
        }
        return "Generation failed: $message"
    }

    private suspend fun ensureEngineLoaded(model: com.orangeisland.app.data.LocalChatModelConfig): LlamaChatEngine? {
        return engineLock.withLock {
            val existing = currentEngine
            if (existing != null && existing.modelPath == model.localFilePath) {
                existing.resetContext()
                // Load or unload mmproj based on current config
                if (model.mmprojPath.isNotBlank()) {
                    existing.loadMmproj(model.mmprojPath)
                } else {
                    existing.unloadMmproj()
                }
                existing
            } else {
                existing?.close()
                val engine = LlamaChatEngine(model.localFilePath, model.nCtx)
                if (engine.load()) {
                    if (model.mmprojPath.isNotBlank()) {
                        val loaded = engine.loadMmproj(model.mmprojPath)
                        DebugLog.d(TAG, "mmproj load: $loaded for ${model.mmprojPath}")
                    }
                    currentEngine = engine
                    engine
                } else {
                    null
                }
            }
        }
    }

    private fun buildTemplateMessages(
        messages: List<ChatMessage>,
        systemPrompt: String?,
        imagePathsOut: MutableList<String>? = null
    ): List<ChatTemplateMessage> {
        val result = mutableListOf<ChatTemplateMessage>()

        if (!systemPrompt.isNullOrBlank()) {
            result.add(ChatTemplateMessage(role = "system", content = systemPrompt))
        }

        for (msg in messages) {
            if (msg.participant == Participant.ERROR) continue

            // Tool call messages: treat as assistant
            if (msg.id.startsWith(Constants.TOOL_MSG_PREFIX)) {
                val toolSegs = msg.segments?.filter { it.type == "tool" }
                if (!toolSegs.isNullOrEmpty()) {
                    for (seg in toolSegs) {
                        result.add(ChatTemplateMessage(
                            role = "assistant",
                            content = "Tool call: ${seg.toolName}\nArguments: ${seg.toolArgs}"
                        ))
                    }
                } else if (msg.toolCall != null) {
                    result.add(ChatTemplateMessage(
                        role = "assistant",
                        content = "Tool call: ${msg.toolCall.toolName}\nArguments: ${msg.toolCall.arguments}"
                    ))
                }
                continue
            }

            // Tool result messages: treat as user (tool results)
            if (msg.id.startsWith(Constants.RESULT_MSG_PREFIX)) {
                val toolSegs = msg.segments?.filter { it.type == "tool" }
                if (!toolSegs.isNullOrEmpty()) {
                    for (seg in toolSegs) {
                        result.add(ChatTemplateMessage(
                            role = "user",
                            content = "Tool result: ${seg.toolResult ?: ""}"
                        ))
                    }
                } else if (msg.toolCall != null) {
                    result.add(ChatTemplateMessage(
                        role = "user",
                        content = "Tool result: ${msg.toolCall.result}"
                    ))
                }
                continue
            }

            // Normal messages
            val role = when (msg.participant) {
                Participant.USER -> "user"
                Participant.MODEL -> "assistant"
                Participant.ERROR -> "user"
            }

            val images = msg.images.filter { it.isNotBlank() }
            val content = if (role == "user" && images.isNotEmpty() && imagePathsOut != null) {
                imagePathsOut.addAll(images)
                images.joinToString("\n") { "<__media__>" } + "\n" + msg.text
            } else {
                msg.text
            }

            result.add(ChatTemplateMessage(role = role, content = content))
        }

        return result
    }

    private fun buildPrompt(messages: List<ChatTemplateMessage>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            sb.append("<|im_start|>${msg.role}\n${msg.content}<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> {
        return settings.localChatModels.first().map { it.modelId }
    }

    fun close() {
        currentEngine?.close()
        currentEngine = null
    }

    suspend fun releaseEngine() {
        engineLock.withLock {
            currentEngine?.close()
            currentEngine = null
        }
    }

    fun releaseEngineBlocking() {
        kotlinx.coroutines.runBlocking { releaseEngine() }
    }
}
