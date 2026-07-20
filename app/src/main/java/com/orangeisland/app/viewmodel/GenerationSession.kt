package com.orangeisland.app.viewmodel

import android.app.Application
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.Participant
import com.orangeisland.app.service.OrangeIslandForegroundService
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the race-free generation lifecycle that was previously inlined in
 * [ChatViewModel]: the IO scope and current job, the send gate, and — most
 * importantly — the two independent ownership signals that keep stop / regenerate /
 * supersede correct:
 *
 *  • [uiGenToken] owns the shared UI state ([isLoading]/[streamingMessage]/
 *    [generatingInConversationId]). Advanced on EVERY stop and read (captured) by
 *    each new generation. The four token-gated mutators below only touch UI state
 *    while their captured token is still current, under [genLock], so a stopped or
 *    superseded generation physically cannot resurrect "Thinking…" or flip the button.
 *
 *  • [persistId] owns the model message's DB row. Advanced ONLY when a new generation
 *    starts (never on stop), so a stopped generation still persists its own text while
 *    a superseded one is blocked from clobbering the newer message.
 *
 * The generation StateFlows and [allMessages] remain owned by ChatViewModel (they are
 * read across the whole VM); this session holds the same instances and mutates them
 * behind the token gate, so the lock/token semantics are unchanged from before.
 */
class GenerationSession(
    private val app: Application,
    private val convRepo: ConversationRepository,
    private val settings: SettingsRepository,
    private val isLoading: MutableStateFlow<Boolean>,
    private val streamingMessage: MutableStateFlow<ChatMessage?>,
    private val generatingInConversationId: MutableStateFlow<String?>,
    private val allMessages: MutableStateFlow<List<ChatMessage>>,
    private val currentConversationId: StateFlow<String?>,
    private val onIndexMessageForRag: (messageId: String, text: String) -> Unit,
    private val onCacheMessages: (modelId: String) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var generationJob: Job? = null
    val sendGate = AtomicBoolean(false)
    @Volatile private var stopFinalizationJob: Job? = null

    private val genLock = Any()
    private var uiGenToken = 0L
    private val persistId = AtomicLong(0L)

    private data class StopFinalizationState(
        val conversationId: String?,
        val messages: List<ChatMessage>
    )

    // ── Ownership accessors used by the VM's generation methods ───────────
    /** Captures the current UI-ownership token right after a stop, under the lock. */
    fun captureUiToken(): Long = synchronized(genLock) { uiGenToken }
    /** Claims DB-row ownership for a freshly-started generation. */
    fun nextPersistId(): Long = persistId.incrementAndGet()
    /** True while [persistId] still belongs to the generation that captured [id]. */
    fun isLatestPersist(id: Long): Boolean = persistId.get() == id

    /**
     * Bundles the five token-gated callbacks for one generation so each call site
     * wires the ownership tokens once instead of re-threading them per lambda.
     */
    fun callbacksFor(uiToken: Long, persistId: Long) = GenerationCallbacks(
        onStreamUpdate = { streamUpdate(uiToken, it) },
        onLoadingChange = { loadingChange(uiToken, it) },
        onGeneratingIdChange = { generatingIdChange(uiToken, it) },
        onStreamClear = { streamClear(uiToken) },
        isLatestPersist = { isLatestPersist(persistId) },
    )

    // ── Token-gated UI mutators ───────────────────────────────────────────
    // Once superseded or stopped, the token no longer matches and every call is a
    // silent no-op — a winding-down generation physically cannot touch the screen.
    fun streamUpdate(uiToken: Long, msg: ChatMessage) {
        synchronized(genLock) { if (uiGenToken == uiToken) streamingMessage.value = msg }
    }
    fun loadingChange(uiToken: Long, value: Boolean) {
        synchronized(genLock) { if (uiGenToken == uiToken) isLoading.value = value }
    }
    fun generatingIdChange(uiToken: Long, id: String?) {
        synchronized(genLock) { if (uiGenToken == uiToken) generatingInConversationId.value = id }
    }
    fun streamClear(uiToken: Long) {
        synchronized(genLock) {
            if (uiGenToken != uiToken) return
            val msg = streamingMessage.value
            if (msg?.status != MessageStatus.STOPPED) {
                if (msg != null) { allMessages.update { it.map { m -> if (m.id == msg.id) msg else m } } }
                streamingMessage.value = null
            }
        }
        val id = settings.activeEmbeddingModelId.value
        if (id.isNotEmpty()) onCacheMessages(id)
    }

    // ── Stop / finalization ───────────────────────────────────────────────
    fun stop() {
        stopInternal(releaseSendGate = true)
    }

    fun stopForReplacement(): Job? = stopInternal(releaseSendGate = false)

    private fun stopInternal(releaseSendGate: Boolean): Job? {
        val previousJob = generationJob
        com.orangeisland.app.api.HttpClient.activeStreamHandle?.cancel()
        previousJob?.cancel()
        // Advance the UI-ownership token and commit the terminal UI state as one
        // atomic step under genLock. Any callback from the cancelled generation that
        // is mid-flight on the IO thread is serialized by the same lock, so it either
        // ran before this (and we overwrite it with STOPPED) or runs after (and is
        // gated out by the advanced token). Either way "Thinking…" can never resurface.
        var stoppedConversationId: String? = null
        val stoppedMsg = synchronized(genLock) {
            uiGenToken += 1
            stoppedConversationId = generatingInConversationId.value ?: currentConversationId.value
            isLoading.value = false
            val s = streamingMessage.value?.copy(status = MessageStatus.STOPPED)
            streamingMessage.value = s
            generatingInConversationId.value = null
            s
        }
        // Reflect STOPPED in memory immediately, then persist that terminal state via
        // a short DB-only job. The cancelled provider may still unwind later, but it
        // no longer blocks the user from sending the next message.
        val fallbackStoppedMessages = mutableListOf<ChatMessage>()
        if (stoppedMsg != null) {
            allMessages.update { it.map { m -> if (m.id == stoppedMsg.id) stoppedMsg else m } }
        } else {
            // streamingMessage was null — mark any in-flight model message directly.
            allMessages.update { it.map { m ->
                if (m.participant == Participant.MODEL &&
                    (m.status == MessageStatus.SENDING || m.status == MessageStatus.THINKING || m.status == MessageStatus.TOOL_CALLING || m.status == MessageStatus.TRANSCRIBING)
                ) {
                    val stopped = m.copy(status = MessageStatus.STOPPED)
                    fallbackStoppedMessages.add(stopped)
                    stopped
                } else m
            } }
        }
        val stoppedMessages = stoppedMsg?.let { listOf(it) } ?: fallbackStoppedMessages
        val finalizationJob = launchStopFinalization(StopFinalizationState(stoppedConversationId, stoppedMessages))
        if (releaseSendGate) sendGate.set(false)
        OrangeIslandForegroundService.stop(app)
        return finalizationJob ?: currentStopFinalizationJob()
    }

    private fun currentStopFinalizationJob(): Job? {
        return synchronized(genLock) { stopFinalizationJob?.takeUnless { it.isCompleted } }
    }

    private fun launchStopFinalization(state: StopFinalizationState): Job? {
        val conversationId = state.conversationId ?: return currentStopFinalizationJob()
        val messages = state.messages.distinctBy { it.id }
        if (messages.isEmpty()) return currentStopFinalizationJob()

        val job = scope.launch {
            try {
                val conversationExists = convRepo.getConversation(conversationId) != null
                if (!conversationExists) return@launch
                for (message in messages) {
                    convRepo.upsertMessage(message.toStoppedEntity(conversationId))
                    if (message.text.isNotBlank() && settings.autoCacheEnabled.value &&
                        (settings.modelSearchMethod.value == Constants.SEARCH_METHOD_RAG || settings.manualSearchMethod.value == Constants.SEARCH_METHOD_RAG)
                    ) {
                        onIndexMessageForRag(message.id, message.text)
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("OrangeIslandVM", "Failed to persist stopped generation", e)
            }
        }
        synchronized(genLock) { stopFinalizationJob = job }
        return job
    }

    private fun ChatMessage.toStoppedEntity(conversationId: String): MessageEntity {
        val toolJson = segments?.let { Json.encodeToString(it) } ?: toolCall?.let {
            Json.encodeToString(listOf(
                MessageSegment(
                    type = "tool",
                    toolName = it.toolName,
                    toolArgs = it.arguments,
                    toolResult = it.result,
                    signature = it.signature,
                    toolCallId = it.toolCallId
                )
            ))
        }
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            parentId = parentId,
            text = text,
            images = images,
            thoughts = thoughts,
            thoughtTitle = thoughtTitle,
            tokenCount = tokenCount,
            status = MessageStatus.STOPPED,
            participant = participant,
            timestamp = timestamp,
            thoughtTimeMs = thoughtTimeMs,
            modelName = modelName,
            toolCallJson = toolJson,
            attachmentMeta = attachmentMeta?.let { Json.encodeToString(it) }
        )
    }

    /** Cancels the IO scope; call from ChatViewModel.onCleared(). */
    fun cancelScope() {
        scope.coroutineContext[Job]?.cancel()
    }
}
