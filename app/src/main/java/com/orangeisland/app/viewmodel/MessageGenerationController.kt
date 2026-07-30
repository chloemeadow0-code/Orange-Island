package com.orangeisland.app.viewmodel

import android.app.Application
import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.api.LlamaEngine
import com.orangeisland.app.api.ProviderConfig
import com.orangeisland.app.api.StreamEvent
import com.orangeisland.app.api.local.LocalProvider
import com.orangeisland.app.data.BuiltInPrompts
import com.orangeisland.app.data.ConversationSettings
import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.data.local.ChatEntity
import com.orangeisland.app.data.local.MessageEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.model.ChatMessage
import com.orangeisland.app.model.MessageSegment
import com.orangeisland.app.model.MessageStatus
import com.orangeisland.app.model.ModelId
import com.orangeisland.app.model.Participant
import com.orangeisland.app.model.SelectedAttachment
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Owns the message lifecycle (send / regenerate / edit / delete) and the
 * race-free generation handshake. Extracted VERBATIM from ChatViewModel.
 * Holds references to the SAME MutableStateFlow instances that ChatViewModel
 * exposes — do NOT create new ones here.
 */
class MessageGenerationController(
    // ── 协程作用域(用 viewModelScope 传进来)──
    private val viewModelScope: CoroutineScope,
    private val application: Application,
    private val appContext: Context,
    // ── 单例协作者 ──
    private val convRepo: ConversationRepository,
    private val settings: SettingsRepository,
    private val session: GenerationSession,
    private val generationManagerProvider: () -> GenerationManager,
    private val requestBuilder: GenerationRequestBuilder,
    private val payloadBuilder: MessagePayloadBuilder,
    private val providerRegistry: ProviderRegistry,
    private val localProvider: LocalProvider,
    // ── 共享 UI 状态:必须是 ChatViewModel 里的同一个实例 ──
    private val allMessages: MutableStateFlow<List<ChatMessage>>,          // = _allMessages
    private val selectedChildren: MutableStateFlow<Map<String?, String>>,  // = _selectedChildren
    private val streamingMessage: MutableStateFlow<ChatMessage?>,          // = _streamingMessage
    private val currentConversationId: MutableStateFlow<String?>,          // = _currentConversationId
    private val isLoading: MutableStateFlow<Boolean>,                      // = _isLoading
    private val generatingInConversationId: MutableStateFlow<String?>,     // = _generatingInConversationId
    private val isNewChatMode: MutableStateFlow<Boolean>,                  // = _isNewChatMode
    private val pendingConversationSettings: MutableStateFlow<ConversationSettings?>, // = _pendingConversationSettings
    private val pendingSystemPromptId: MutableStateFlow<String?>,          // = _pendingSystemPromptId
    private val pendingProjectId: MutableStateFlow<String?>,               // = _pendingProjectId
    private val currentActiveModel: StateFlow<String>,                     // = currentActiveModel(只读)
    private val messages: StateFlow<List<ChatMessage>>,                    // = messages(只读)
    // ── 回调:替换掉方法体里对 ChatViewModel 私有成员/方法的调用 ──
    private val onScrollToMessage: (String?) -> Unit,    // 替换 triggerScrollToMessage(...)
    private val onSnackbar: (String) -> Unit,            // 替换 emitSnackbar(...)
    private val onSnackbarSuspend: suspend (String) -> Unit,  // generateTitle 内的顺序 emit(等价原版 _snackbarMessage.emit）
    private val onPersistSelectedChildren: suspend (String, Map<String?, String>) -> Unit,
    // Called when sendMessage creates a NEW conversation, so the UI can suppress the
    // conversation-open auto-scroll (the send's own scroll-to-message handles it) and
    // avoid a double scroll on the first message of a new chat.
    private val onConversationCreatedBySend: () -> Unit = {},
) {
    private val generationManager: GenerationManager get() = generationManagerProvider()

    // ════════════════════════════════════════════════════════════════════
    // deleteMessage
    // ════════════════════════════════════════════════════════════════════

    /**
     * Deletes a message and all its descendants (BFS cascade).
     * Hidden tool_/result_ children are included in the cascade.
     * Attachments, embeddings, and branch selections are cleaned up.
     * Returns the count of deleted messages (for the confirmation dialog).
     */
    fun deleteMessage(messageId: String): Int {
        val currentId = currentConversationId.value ?: return 0

        // Synchronous snapshot for dialog count return — must stay on the calling thread.
        val snapshot = allMessages.value
        val targetMsg = snapshot.find { it.id == messageId } ?: return 0

        val previewIds = linkedSetOf(messageId)
        val queue = mutableListOf(messageId)
        while (queue.isNotEmpty()) {
            val pid = queue.removeAt(0)
            snapshot.filter { it.parentId == pid }.forEach {
                if (previewIds.add(it.id)) queue.add(it.id)
            }
        }

        // P1: Only stop generation if deleting within the currently-generating conversation.
        // P0: Use stopForReplacement() + join() to prevent the STOPPED-upsert race
        //     that can resurrect deleted messages (the only write path that was missing it).
        val stopFinalization = if (generatingInConversationId.value == currentId) {
            session.stopForReplacement()
        } else {
            null
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Wait for STOPPED DB finalization to complete before deleting.
            // Without this join, a concurrent upsertMessage from stop finalization
            // could resurrect the deleted row as a zombie/orphan after our DELETE.
            stopFinalization?.join()

            // Recompute staleIds from the latest allMessages after join(),
            // in case the message tree changed during finalization.
            val allMsgs = allMessages.value
            if (allMsgs.none { it.id == messageId }) return@launch  // already deleted during wait
            val staleIds = linkedSetOf(messageId)
            val queue = mutableListOf(messageId)
            while (queue.isNotEmpty()) {
                val pid = queue.removeAt(0)
                allMsgs.filter { it.parentId == pid }.forEach {
                    if (staleIds.add(it.id)) queue.add(it.id)
                }
            }

            val staleList = allMsgs.filter { it.id in staleIds }
            convRepo.deleteMessageFiles(staleList)

            // Delete embeddings for all cascaded messages
            for (id in staleIds) {
                convRepo.deleteEmbedding(id)
            }

            // DB delete
            convRepo.deleteMessagesByIds(staleIds.toList())

            // Update allMessages
            allMessages.update { it.filter { m -> m.id !in staleIds } }

            // Fix selectedChildren — remove entries where key or value is deleted.
            // If a deleted message was the selected branch, switch to the next available sibling.
            val remainingMsgs = allMessages.value
            val newSelected = selectedChildren.value.toMutableMap()
            var changed = false
            for ((parentId, childId) in selectedChildren.value) {
                // Remove entry if the parent itself was deleted
                if (parentId != null && parentId in staleIds) {
                    newSelected.remove(parentId)
                    changed = true
                    continue
                }
                if (childId in staleIds) {
                    val siblings = remainingMsgs.filter {
                        it.parentId == parentId &&
                            !it.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                            !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
                    }.sortedBy { it.timestamp }
                    if (siblings.isNotEmpty()) {
                        newSelected[parentId] = siblings.last().id
                    } else {
                        newSelected.remove(parentId)
                    }
                    changed = true
                }
            }
            if (changed) selectedChildren.value = newSelected
        }

        return previewIds.size
    }

    // ════════════════════════════════════════════════════════════════════
    // regenerate
    // ════════════════════════════════════════════════════════════════════

    fun regenerate(messageId: String) {
        val currentId = currentConversationId.value ?: return
        val modelId = currentActiveModel.value
        val (providerName, activeKey) = requestBuilder.resolveProviderKey(modelId) ?: return

        val stopFinalization = session.stopForReplacement()
        // Capture ownership on the UI thread, immediately after stopGeneration advanced
        // the token, so no concurrent stop can slip in before we record it.
        val myUiToken = session.captureUiToken()

        // Compute IDs and set placeholder on the calling thread before launching IO work,
        // so the combine function never sees streamingMessage=null while the error is in allMessages.
        val messageToRegenerate = allMessages.value.find { it.id == messageId } ?: return
        val parentId = messageToRegenerate.parentId ?: return
        val isErrorOrStopped = messageToRegenerate.status == MessageStatus.ERROR || messageToRegenerate.status == MessageStatus.STOPPED
        val isLatest = allMessages.value.none { it.parentId == messageId && !it.id.startsWith(Constants.TOOL_MSG_PREFIX) && !it.id.startsWith(Constants.RESULT_MSG_PREFIX) }
        // Error/stopped: purge and replace in-place. Normal: create new branch.
        val modelMessageId = if (isErrorOrStopped && isLatest) messageId else UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis() + 1

        // Insert placeholder into allMessages and update selectedChildren on the calling
        // thread BEFORE setting streamingMessage. This ensures the combine function sees a
        // consistent state where the new ID is both present and selected, avoiding a frame
        // where two model messages appear in the path.
        val placeholder = ChatMessage(
            id = modelMessageId, parentId = parentId, text = "", participant = Participant.MODEL,
            status = MessageStatus.SENDING, timestamp = startTime
        )
        allMessages.update { it.filter { m -> m.id != modelMessageId } + placeholder }
        val newMap = selectedChildren.value.toMutableMap()
        newMap[parentId] = modelMessageId
        val selectedAfterRegenerate = newMap.toMap()
        selectedChildren.value = selectedAfterRegenerate

        streamingMessage.value = placeholder
        isLoading.value = true

        session.generationJob = session.scope.launch {
            // Wait only for the short STOPPED DB finalization. The cancelled provider
            // may still be unwinding, but it no longer owns the next generation path.
            stopFinalization?.join()
            val myPersistId = session.nextPersistId()
            try {
                allMessages.value.find { it.id == parentId } ?: return@launch

                if (isErrorOrStopped && isLatest) {
                    // Purge stale tool call children, thinking content, and embeddings
                    val allMsgs = allMessages.value
                    val staleIds = mutableListOf<String>()
                    val queue = mutableListOf(modelMessageId)
                    while (queue.isNotEmpty()) {
                        val pid = queue.removeAt(0)
                        allMsgs.filter { it.parentId == pid && (it.id.startsWith(Constants.TOOL_MSG_PREFIX) || it.id.startsWith(Constants.RESULT_MSG_PREFIX)) }
                            .forEach { staleIds.add(it.id); queue.add(it.id) }
                    }
                    if (staleIds.isNotEmpty()) {
                        convRepo.deleteMessagesByIds(staleIds)
                        allMessages.update { it.filter { m -> m.id !in staleIds } }
                    }
                    convRepo.deleteEmbedding(modelMessageId)
                    convRepo.upsertMessage(MessageEntity(
                        id = modelMessageId, conversationId = currentId, parentId = parentId,
                        text = "", thoughts = null, thoughtTitle = null, status = MessageStatus.SENDING, participant = Participant.MODEL, timestamp = startTime,
                        modelName = currentActiveModel.value, toolCallJson = null
                    ))
                } else {
                    // New branch — old message and its tool calls stay as a selectable branch
                    convRepo.upsertMessage(MessageEntity(
                        id = modelMessageId, conversationId = currentId, parentId = parentId,
                        text = "", thoughts = null, status = MessageStatus.SENDING, participant = Participant.MODEL, timestamp = startTime,
                        modelName = currentActiveModel.value
                    ))
                }
                onPersistSelectedChildren(currentId, selectedAfterRegenerate)
                convRepo.getConversation(currentId)?.let { conv ->
                    convRepo.upsertConversation(conv.copy(lastUpdated = System.currentTimeMillis()))
                }
                launchGeneration(
                    currentId, modelMessageId, startTime,
                    isRegenerate = true, replaceMessageId = messageId,
                    providerName, modelId, activeKey, myUiToken, myPersistId,
                    callerTag = "regenerate"
                )
                val finalMsg = allMessages.value.find { it.id == modelMessageId }
                if (finalMsg?.status == MessageStatus.ERROR) {
                    UsageLogManager.log(
                        UsageLogManager.Type.CONVERSATION,
                        name = "regenerate",
                        conversationId = currentId,
                        details = "生成失败: 消息状态为 ERROR",
                        isError = true
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.orangeisland.app.util.DebugLog.e("MessageGenerationController", "regenerate failed", e)
                UsageLogManager.log(
                    UsageLogManager.Type.CONVERSATION,
                    name = "regenerate",
                    conversationId = currentId,
                    details = "重新生成失败: ${e.message}",
                    isError = true
                )
            } finally {
                session.loadingChange(myUiToken, false)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // launchGeneration
    // ════════════════════════════════════════════════════════════════════

    /**
     * Shared generation tail called by [sendMessage], [regenerate], and
     * [editMessage]: resolves system prompt + conversation settings, builds
     * [GenerationConfig]/[GenerationContext], and launches the provider stream.
     *
     * All three entry points converge here after their differing branch-setup
     * heads, eliminating copy-pasted prompt-resolution / config-building /
     * callback-wiring code.
     */
    private suspend fun launchGeneration(
        currentId: String,
        modelMessageId: String,
        startTime: Long,
        isRegenerate: Boolean,
        replaceMessageId: String?,
        providerName: String,
        modelId: String,
        activeKey: String,
        uiToken: Long,
        persistId: Long,
        callerTag: String,
        onTitleTriggerReady: ((String, String) -> Unit)? = null
    ) {
        val t0 = System.currentTimeMillis()
        val resolved = requestBuilder.buildEffectiveSystemPrompt(currentId)
        DebugLog.d("GenPerf", "buildSystemPrompt: ${System.currentTimeMillis() - t0}ms")

        val t1 = System.currentTimeMillis()
        val effectiveSettings = requestBuilder.buildEffectiveConversationSettings(currentId)
        DebugLog.d("GenPerf", "buildSettings: ${System.currentTimeMillis() - t1}ms")

        // Re-resolve the key against on-disk settings here (the suspend convergence
        // point for all entry paths). The synchronous [activeKey] resolved by the
        // callers can be blank if DataStore had not finished loading when Send was
        // tapped, which would build the request with an empty key → 401.
        val t2 = System.currentTimeMillis()
        val freshKey = settings.awaitActiveKey(providerName)?.takeIf { it.isNotBlank() } ?: activeKey
        DebugLog.d("GenPerf", "awaitActiveKey: ${System.currentTimeMillis() - t2}ms")

        val t3 = System.currentTimeMillis()
        val (config, genCtx) = requestBuilder.buildGenerationPair(
            providerName, modelId, freshKey,
            resolved.systemPrompt, resolved.userPrepend, resolved.userPostpend,
            effectiveSettings, currentId, resolved.projectId, resolved.systemPromptId
        )
        DebugLog.d("GenPerf", "buildGenerationPair: ${System.currentTimeMillis() - t3}ms")
        try {
            generationManager.generate(
                conversationId = currentId,
                modelMessageId = modelMessageId,
                startTime = startTime,
                isRegenerate = isRegenerate,
                replaceMessageId = replaceMessageId,
                modelName = currentActiveModel.value,
                config = config,
                ctx = genCtx,
                generationJob = session.generationJob,
                callbacks = session.callbacksFor(uiToken, persistId, onTitleTriggerReady),
                session = session
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.e("OrangeIslandVM", "Generation failed in $callerTag", e)
            UsageLogManager.log(
                UsageLogManager.Type.CONVERSATION,
                name = callerTag,
                conversationId = currentId,
                details = "生成失败: ${e.message}",
                isError = true
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // editMessage
    // ════════════════════════════════════════════════════════════════════

    fun editMessage(messageId: String, newText: String) {
        val currentId = currentConversationId.value ?: return
        val modelId = currentActiveModel.value
        val (providerName, activeKey) = requestBuilder.resolveProviderKey(modelId) ?: return

        val stopFinalization = session.stopForReplacement()
        val myUiToken = session.captureUiToken()
        // Set loading synchronously on the calling thread (like sendMessage/regenerate)
        // so the global generation gate disables all per-message actions immediately,
        // with no window during stopFinalization.join() + DB setup.
        isLoading.value = true
        session.generationJob = session.scope.launch {
            stopFinalization?.join()
            val myPersistId = session.nextPersistId()
            try {
            val messageToEdit = allMessages.value.find { it.id == messageId } ?: return@launch
            val newUserMessageId = UUID.randomUUID().toString()
            convRepo.upsertMessage(MessageEntity(
                id = newUserMessageId, conversationId = currentId, parentId = messageToEdit.parentId,
                text = newText, thoughts = null, status = MessageStatus.SUCCESS, participant = Participant.USER, timestamp = System.currentTimeMillis()
            ))
            val newMap = selectedChildren.value.toMutableMap()
            newMap[messageToEdit.parentId] = newUserMessageId
            val selectedAfterUserEdit = newMap.toMap()
            selectedChildren.value = selectedAfterUserEdit
            onPersistSelectedChildren(currentId, selectedAfterUserEdit)
            val modelMessageId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis() + 1
            convRepo.upsertMessage(MessageEntity(
                id = modelMessageId, conversationId = currentId, parentId = newUserMessageId,
                text = "", thoughts = null, status = MessageStatus.SENDING, participant = Participant.MODEL, timestamp = startTime,
                modelName = currentActiveModel.value
            ))
            convRepo.getConversation(currentId)?.let { conv ->
                convRepo.upsertConversation(conv.copy(lastUpdated = System.currentTimeMillis()))
            }
            // Set streamingMessage BEFORE allMessages so the combine never
            // evaluates with stale allMessages data but no streaming overlay.
            val placeholder = ChatMessage(
                id = modelMessageId, parentId = newUserMessageId, text = "", participant = Participant.MODEL,
                status = MessageStatus.SENDING, timestamp = startTime, modelName = currentActiveModel.value
            )
            session.streamUpdate(myUiToken, placeholder)
            allMessages.update { it.filter { m -> m.id != modelMessageId } + placeholder }
            val editChildren = selectedAfterUserEdit.toMutableMap()
            editChildren[newUserMessageId] = modelMessageId
            val selectedAfterModelEdit = editChildren.toMap()
            selectedChildren.value = selectedAfterModelEdit
            onPersistSelectedChildren(currentId, selectedAfterModelEdit)
            launchGeneration(
                currentId, modelMessageId, startTime,
                isRegenerate = false, replaceMessageId = null,
                providerName, modelId, activeKey, myUiToken, myPersistId,
                callerTag = "editMessage"
            )
            val finalMsg = allMessages.value.find { it.id == modelMessageId }
            if (finalMsg?.status == MessageStatus.ERROR) {
                UsageLogManager.log(
                    UsageLogManager.Type.CONVERSATION,
                    name = "editMessage",
                    conversationId = currentId,
                    details = "生成失败: 消息状态为 ERROR",
                    isError = true
                )
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.orangeisland.app.util.DebugLog.e("MessageGenerationController", "editMessage failed", e)
                UsageLogManager.log(
                    UsageLogManager.Type.CONVERSATION,
                    name = "editMessage",
                    conversationId = currentId,
                    details = "编辑消息失败: ${e.message}",
                    isError = true
                )
            } finally {
                session.loadingChange(myUiToken, false)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // editAssistantMessage — text-only correction for an AI reply. Creates a
    // new sibling branch (same parentId, auto-selected) carrying the edited
    // text — NEVER calls the model. If the original message had segments
    // (thought/tool/answer blocks), all "answer" segments are collapsed into
    // one at the position of the first, holding the edited text; thought/tool
    // segments are preserved untouched so the visible tool-call history stays
    // intact. Only meant to be invoked once generation has finished (the UI
    // gates the entry point on isEditingAllowed / !isLoading).
    // ════════════════════════════════════════════════════════════════════

    fun editAssistantMessage(messageId: String, newText: String) {
        if (isLoading.value) return
        val currentId = currentConversationId.value ?: return
        val messageToEdit = allMessages.value.find { it.id == messageId } ?: return
        if (messageToEdit.participant != Participant.MODEL) return

        val parentId = messageToEdit.parentId
        val originalSegments = messageToEdit.segments
        val newSegments: List<MessageSegment>? = originalSegments?.let { segs ->
            // GenerationManager seeds every generation with a blank placeholder answer
            // segment at position 0 (`mutableListOf(MessageSegment(type = "answer"))`),
            // which stays untouched — and stays in FIRST place — whenever the model thinks
            // or calls a tool before writing any visible text. Anchoring the edited text on
            // "the first answer segment" would land on that invisible placeholder instead of
            // the real one, shoving thought/tool blocks that actually happened first down
            // below the edited answer. Drop blank answer segments before anchoring so the
            // edit lands where the real (rendered) answer segment was.
            val cleaned = segs.filterNot { it.type == "answer" && it.content.isBlank() }
            val hadAnswer = cleaned.any { it.type == "answer" }
            if (hadAnswer) {
                val result = mutableListOf<MessageSegment>()
                var answerInserted = false
                for (seg in cleaned) {
                    if (seg.type == "answer") {
                        if (!answerInserted) {
                            result.add(MessageSegment(type = "answer", content = newText))
                            answerInserted = true
                        }
                        // Subsequent answer segments are merged away — only one survives.
                    } else {
                        result.add(seg)
                    }
                }
                result
            } else {
                cleaned + MessageSegment(type = "answer", content = newText)
            }
        }

        val newBranchId = UUID.randomUUID().toString()
        val newTimestamp = System.currentTimeMillis()
        val newBranch = messageToEdit.copy(
            id = newBranchId,
            text = newText,
            segments = newSegments,
            status = MessageStatus.SUCCESS,
            timestamp = newTimestamp
        )

        // No placeholder/streaming phase needed — this isn't a generation, so the
        // branch can just appear as a finished SUCCESS message immediately.
        allMessages.update { it + newBranch }
        val newMap = selectedChildren.value.toMutableMap()
        newMap[parentId] = newBranchId
        val selectedAfterEdit = newMap.toMap()
        selectedChildren.value = selectedAfterEdit

        viewModelScope.launch(Dispatchers.IO) {
            convRepo.upsertMessage(MessageEntity(
                id = newBranchId, conversationId = currentId, parentId = parentId,
                text = newText,
                images = messageToEdit.images,
                audio = messageToEdit.audio,
                thoughts = messageToEdit.thoughts,
                thoughtTitle = messageToEdit.thoughtTitle,
                tokenCount = messageToEdit.tokenCount,
                cachedTokenCount = messageToEdit.cachedTokenCount,
                contextMessageCount = messageToEdit.contextMessageCount,
                status = MessageStatus.SUCCESS,
                participant = Participant.MODEL,
                timestamp = newTimestamp,
                thoughtTimeMs = messageToEdit.thoughtTimeMs,
                generationDurationMs = messageToEdit.generationDurationMs,
                modelName = messageToEdit.modelName,
                toolCallJson = newSegments?.let { Json.encodeToString(it) }
            ))
            onPersistSelectedChildren(currentId, selectedAfterEdit)
            convRepo.getConversation(currentId)?.let { conv ->
                convRepo.upsertConversation(conv.copy(lastUpdated = System.currentTimeMillis()))
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // sendMessage
    // ════════════════════════════════════════════════════════════════════

    fun sendMessage(text: String, images: List<String> = emptyList(), attachments: List<SelectedAttachment> = emptyList()): Boolean {
        if (!session.sendGate.compareAndSet(false, true)) return false
        var committed = false
        try {
        val modelId = currentActiveModel.value
        if (modelId.isBlank()) {
            onSnackbar(application.getString(R.string.no_model_selected))
            return false
        }
        val (providerName, activeKey) = requestBuilder.resolveProviderKey(modelId) ?: return false
        if (providerName == Constants.PROVIDER_LOCAL) {
            val localModelId = modelId.substringAfter("${Constants.PROVIDER_LOCAL}:")
            val config = settings.localChatModels.value.find { it.modelId == localModelId }
            if (config == null || !java.io.File(config.localFilePath).exists()) {
                onSnackbar(application.getString(R.string.local_model_not_found))
                return false
            }
        }
        val stopFinalization = session.stopForReplacement()
        // Set loading immediately so UI shows sending state during attachment processing
        isLoading.value = true
        // Capture ownership on the UI thread right after stopGeneration advanced the token.
        val myUiToken = session.captureUiToken()

        committed = true
        session.generationJob = session.scope.launch {
            var currentId: String? = null
            try {
            // Wait only for the short STOPPED DB finalization. The cancelled provider
            // may still be unwinding, but it no longer owns the next generation path.
            val tStopJoin = System.currentTimeMillis()
            stopFinalization?.join()
            DebugLog.d("GenPerf", "stopJoin: ${System.currentTimeMillis() - tStopJoin}ms")

            val myPersistId = session.nextPersistId()
            val tPayload = System.currentTimeMillis()
            val (allImages, attachmentMeta) = payloadBuilder.buildMessagePayload(application, images, attachments)
            DebugLog.d("GenPerf", "buildPayload: ${System.currentTimeMillis() - tPayload}ms, images=${allImages.size}, attachments=${attachments.size}")
            currentId = currentConversationId.value
            val wasNewChat = isNewChatMode.value
            if (wasNewChat || currentId == null) {
                val newId = UUID.randomUUID().toString()
                convRepo.upsertConversation(ChatEntity(id = newId, title = appContext.getString(R.string.new_chat), modelId = currentActiveModel.value, systemPromptId = pendingSystemPromptId.value, projectId = pendingProjectId.value))
                // Suppress the conversation-open auto-scroll BEFORE the id change triggers it.
                onConversationCreatedBySend()
                currentConversationId.value = newId
                isNewChatMode.value = false
                currentId = newId
            }
            // Apply pending per-conversation settings if any (from Advanced dialog in new chat)
            val pendingSettings = pendingConversationSettings.value
            if (pendingSettings != null) {
                settings.setConversationSettings(currentId, pendingSettings)
                pendingConversationSettings.value = null
            }
            // Walk root → tail mirroring ConversationUiState.resolvePath EXACTLY (see the
            // matching comment in WorkflowRunner.buildChatMessageRunner). The previous version
            // broke when a message had only tool_/result_ children, parenting the new message
            // mid-conversation and hiding everything after it.
            val dbMessages = convRepo.getMessagesForConversationSnapshot(currentId)
            var tail: String? = null
            var cursor: String? = null
            val msgWalked = mutableSetOf<String>()
            while (true) {
                val siblings = dbMessages.filter { it.id !in msgWalked && it.parentId == cursor }
                    .sortedBy { it.timestamp }
                if (siblings.isEmpty()) break
                val visibleSibs = siblings.filter {
                    !it.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                    !it.id.startsWith(Constants.RESULT_MSG_PREFIX)
                }
                val selected = if (visibleSibs.isNotEmpty()) visibleSibs.last() else siblings.last()
                msgWalked.add(selected.id)
                cursor = selected.id
                if (!selected.id.startsWith(Constants.TOOL_MSG_PREFIX) &&
                    !selected.id.startsWith(Constants.RESULT_MSG_PREFIX)) {
                    tail = selected.id
                }
            }
            val lastMessageId = tail
                ?: dbMessages.filter {
                    it.participant == Participant.USER || it.participant == Participant.MODEL
                }.maxByOrNull { it.timestamp }?.id

            val userMessageId = UUID.randomUUID().toString()
            convRepo.upsertMessage(MessageEntity(
                id = userMessageId, conversationId = currentId, parentId = lastMessageId,
                text = text, images = allImages, thoughts = null, status = MessageStatus.SUCCESS, participant = Participant.USER, timestamp = System.currentTimeMillis(),
                attachmentMeta = attachmentMeta?.let { kotlinx.serialization.json.Json.encodeToString(it) }
            ))
            settings.incrementMessagesSent()
            val modelMessageId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis() + 1
            convRepo.upsertMessage(MessageEntity(
                id = modelMessageId, conversationId = currentId, parentId = userMessageId,
                text = "", thoughts = null, status = MessageStatus.SENDING, participant = Participant.MODEL, timestamp = startTime,
                modelName = currentActiveModel.value
            ))
            convRepo.getConversation(currentId)?.let { c ->
                convRepo.upsertConversation(c.copy(lastUpdated = System.currentTimeMillis()))
            }
            // Append the new user→model pair to the IN-MEMORY branch map only. Do not read
            // selectedBranchesJson from the DB — that races with the persist collector and
            // can collapse the conversation on some devices.
            val newChildren = selectedChildren.value.toMutableMap()
            if (lastMessageId != null) newChildren[lastMessageId] = userMessageId
            newChildren[userMessageId] = modelMessageId
            selectedChildren.value = newChildren
            // Set streamingMessage BEFORE allMessages, so when the combine
            // re-evaluates on the allMessages change, streamingMessage is already
            // visible — eliminating the single-frame gap.
            val placeholder = ChatMessage(
                id = modelMessageId, parentId = userMessageId, text = "", participant = Participant.MODEL,
                status = MessageStatus.SENDING, timestamp = startTime, modelName = currentActiveModel.value
            )
            session.streamUpdate(myUiToken, placeholder)
            allMessages.update { it.filter { m -> m.id != modelMessageId } + placeholder }
            onScrollToMessage(userMessageId)

            val titleGenerated = java.util.concurrent.atomic.AtomicBoolean(false)

            fun triggerTitle(partialAnswer: String, partialThoughts: String) {
                if (!titleGenerated.compareAndSet(false, true)) return
                viewModelScope.launch {
                    generateTitleFromPartialContent(currentId, partialAnswer, partialThoughts)
                }
            }

            launchGeneration(
                currentId, modelMessageId, startTime,
                isRegenerate = false, replaceMessageId = null,
                providerName, modelId, activeKey, myUiToken, myPersistId,
                callerTag = "sendMessage",
                onTitleTriggerReady = if (wasNewChat && settings.titleGenerationEnabled.value) ::triggerTitle else null
            )

            val lastMsg = allMessages.value.find { it.id == modelMessageId }
            if (lastMsg?.status == MessageStatus.ERROR) {
                UsageLogManager.log(
                    UsageLogManager.Type.CONVERSATION,
                    name = "sendMessage",
                    conversationId = currentId,
                    details = "生成失败: 消息状态为 ERROR",
                    isError = true
                )
            }
            if (wasNewChat && settings.titleGenerationEnabled.value && !titleGenerated.get() && lastMsg?.status != MessageStatus.ERROR) {
                generateTitle(currentId)
            }
            // Auto-compress: fire-and-forget after a successful reply. compressHistory()
            // itself re-checks the path length against maxContextWindow and no-ops if the
            // conversation is still within the window, so this is safe to call every turn.
            if (settings.autoCompressEnabled.value && lastMsg?.status != MessageStatus.ERROR) {
                compressHistory(currentId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.orangeisland.app.util.DebugLog.e("MessageGenerationController", "sendMessage failed", e)
            UsageLogManager.log(
                UsageLogManager.Type.CONVERSATION,
                name = "sendMessage",
                conversationId = currentId,
                details = "发送消息失败: ${e.message}",
                isError = true
            )
        } finally {
            // Token-gated: only the still-current generation clears the button, so a
            // cancelled/superseded coroutine can't revert the icon mid-generation.
            session.loadingChange(myUiToken, false)
            // sendGate must ALWAYS be freed, even when this coroutine was cancelled
            // by a subsequent regenerate(). Otherwise the send button stays locked.
            session.sendGate.set(false)
        }
        } // end launch
    } finally {
        if (!committed) session.sendGate.set(false)
    }
        return true
    }

    // ════════════════════════════════════════════════════════════════════
    // generateTitleFromPartialContent
    // ════════════════════════════════════════════════════════════════════

    private fun generateTitleFromPartialContent(
        conversationId: String,
        partialAnswer: String,
        partialThoughts: String
    ) {
        viewModelScope.launch {
            val conversation = convRepo.getConversation(conversationId) ?: return@launch
            val entities = convRepo.getMessagesForConversationSnapshot(conversationId)
            val path = ConversationUiState.resolvePath(
                allMessages = entities.map {
                    ChatMessage(
                        id = it.id,
                        parentId = it.parentId,
                        text = it.text,
                        participant = it.participant,
                        timestamp = it.timestamp,
                        status = it.status,
                        modelName = it.modelName
                    )
                },
                streamingMsg = null,
                selectedChildren = emptyMap()
            )
            val firstUserMsg = path.firstOrNull { it.participant == Participant.USER } ?: return@launch

            val titleModelId = settings.titleGenerationModel.value
            val modelIdWithPrefix = if (!titleModelId.isNullOrBlank()) titleModelId else (conversation.modelId ?: settings.selectedModel.value)
            val modelId = ModelId.parse(modelIdWithPrefix).modelName
            val (providerName, activeKey) = requestBuilder.resolveProviderKey(modelIdWithPrefix) ?: return@launch

            val assistantPreview = partialAnswer.takeIf { it.isNotBlank() } ?: partialThoughts
            val summaryText = if (assistantPreview.isNotBlank()) {
                "User: ${firstUserMsg.text}\nAssistant: ${assistantPreview.take(500)}"
            } else {
                firstUserMsg.text
            }

            val titlePrompt = listOf(
                ChatMessage(
                    text = "Generate a short title (5 words maximum) for this conversation:\n\n$summaryText\n\nRespond with ONLY the title text, no quotes, no punctuation, no explanation.",
                    participant = Participant.USER,
                    status = MessageStatus.SUCCESS
                )
            )

            val provider = providerRegistry.getInstance(providerName)
                ?: error("Provider '$providerName' is not registered")
            val config = ProviderConfig(
                apiKey = activeKey,
                modelId = modelId,
                systemPrompt = settings.titleGenerationPrompt.value.ifBlank { BuiltInPrompts.TITLE_GENERATION_SYSTEM },
                maxContextWindow = 1,
                thinkingEnabled = false,
                baseUrl = providerRegistry.getEffectiveBaseUrl(providerName)
            )

            var title = ""
            try {
                if (providerName == Constants.PROVIDER_LOCAL) {
                    LlamaEngine.modelMutex.withLock {
                        withContext(Dispatchers.IO) {
                            provider.generateResponse(titlePrompt, config).collect { event ->
                                if (event is StreamEvent.TextChunk) title += event.text
                                else if (event is StreamEvent.Error) DebugLog.e("OrangeIslandVM", "Title generation error: ${event.message}")
                            }
                        }
                        localProvider.releaseEngine()
                    }
                } else {
                    provider.generateResponse(titlePrompt, config).collect { event ->
                        if (event is StreamEvent.TextChunk) title += event.text
                        else if (event is StreamEvent.Error) DebugLog.e("OrangeIslandVM", "Title generation error: ${event.message}")
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("OrangeIslandVM", "Title generation failed for provider=$providerName model=$modelId", e)
                return@launch
            }

            title = title.trim().replace("\n", " ").take(60)
            if (title.isNotBlank()) {
                convRepo.getConversation(conversationId)?.let { existing ->
                    convRepo.upsertConversation(existing.copy(title = title))
                }
                onSnackbarSuspend(appContext.getString(R.string.snackbar_title_generated))
            } else {
                onSnackbarSuspend(appContext.getString(R.string.snackbar_title_error))
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // generateTitle
    // ════════════════════════════════════════════════════════════════════

    fun generateTitle(conversationId: String) {
        viewModelScope.launch {
            onSnackbarSuspend(appContext.getString(R.string.snackbar_generating_title))
            val conversation = convRepo.getConversation(conversationId) ?: return@launch
            // Resolve the TARGET conversation's own path — not messages.value, which
            // is the currently-open conversation. Otherwise a long-press "regenerate
            // title" on a background conversation would summarize the active one.
            val entities = convRepo.getMessagesForConversationSnapshot(conversationId)
            val path = ConversationUiState.resolvePath(
                allMessages = entities.map {
                    ChatMessage(
                        id = it.id,
                        parentId = it.parentId,
                        text = it.text,
                        participant = it.participant,
                        timestamp = it.timestamp,
                        status = it.status,
                        modelName = it.modelName
                    )
                },
                streamingMsg = null,
                selectedChildren = emptyMap()
            )
            val firstUserMsg = path.firstOrNull { it.participant == Participant.USER } ?: return@launch
            val firstModelMsg = path
                .filter { it.participant == Participant.MODEL && it.text.isNotBlank() }
                .firstOrNull()

            val titleModelId = settings.titleGenerationModel.value
            val modelIdWithPrefix = if (!titleModelId.isNullOrBlank()) titleModelId else (conversation.modelId ?: firstModelMsg?.modelName ?: settings.selectedModel.value)
            val modelId = ModelId.parse(modelIdWithPrefix).modelName
            val (providerName, activeKey) = requestBuilder.resolveProviderKey(modelIdWithPrefix) ?: return@launch

            val summaryText = if (firstModelMsg != null) {
                "User: ${firstUserMsg.text}\nAssistant: ${firstModelMsg.text.take(500)}"
            } else {
                firstUserMsg.text
            }

            val titlePrompt = listOf(
                ChatMessage(
                    text = "Generate a short title (5 words maximum) for this conversation:\n\n$summaryText\n\nRespond with ONLY the title text, no quotes, no punctuation, no explanation.",
                    participant = Participant.USER,
                    status = MessageStatus.SUCCESS
                )
            )

            val provider = providerRegistry.getInstance(providerName)
                ?: error("Provider '$providerName' is not registered")
            val config = ProviderConfig(
                apiKey = activeKey,
                modelId = modelId,
                systemPrompt = settings.titleGenerationPrompt.value.ifBlank { BuiltInPrompts.TITLE_GENERATION_SYSTEM },
                maxContextWindow = 1,
                thinkingEnabled = false,
                baseUrl = providerRegistry.getEffectiveBaseUrl(providerName)
            )

            var title = ""
            try {
                // Serialize with embedding to avoid dual model load OOM
                if (providerName == Constants.PROVIDER_LOCAL) {
                    LlamaEngine.modelMutex.withLock {
                        withContext(Dispatchers.IO) {
                            provider.generateResponse(titlePrompt, config).collect { event ->
                                if (event is StreamEvent.TextChunk) title += event.text
                                else if (event is StreamEvent.Error) DebugLog.e("OrangeIslandVM", "Title generation error: ${event.message}")
                            }
                        }
                        localProvider.releaseEngine()
                    }
                } else {
                    provider.generateResponse(titlePrompt, config).collect { event ->
                        if (event is StreamEvent.TextChunk) title += event.text
                        else if (event is StreamEvent.Error) DebugLog.e("OrangeIslandVM", "Title generation error: ${event.message}")
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("OrangeIslandVM", "Title generation failed for provider=$providerName model=$modelId", e)
                return@launch
            }

            title = title.trim().replace("\n", " ").take(60)
            if (title.isNotBlank()) {
                convRepo.getConversation(conversationId)?.let { existing ->
                    convRepo.upsertConversation(existing.copy(title = title))
                }
                onSnackbarSuspend(appContext.getString(R.string.snackbar_title_generated))
            } else {
                onSnackbarSuspend(appContext.getString(R.string.snackbar_title_error))
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // compressHistory — fold older messages into a running summary so long
    // chats keep long-term context without overflowing the context window.
    // Mirrors generateTitle()'s shape (same provider/key resolution, same
    // LlamaEngine.modelMutex discipline for the local provider).
    // ════════════════════════════════════════════════════════════════════

    private val compressingConversationIds = mutableSetOf<String>()

    private fun resolveModelContextLimit(modelIdWithPrefix: String): Int {
        val parsed = ModelId.parse(modelIdWithPrefix)
        return if (parsed.providerName == Constants.PROVIDER_LOCAL) {
            settings.localChatModels.value
                .find { it.modelId == parsed.modelName }
                ?.nCtx
                ?: SettingsRepository.DEFAULT_MODEL_CONTEXT_LIMIT
        } else {
            settings.modelContextLimits.value[modelIdWithPrefix]
                ?: SettingsRepository.DEFAULT_MODEL_CONTEXT_LIMIT
        }
    }

    fun compressHistory(conversationId: String) {
        if (!compressingConversationIds.add(conversationId)) {
            return  // already compressing
        }
        viewModelScope.launch {
            try {
                val conversation = convRepo.getConversation(conversationId)
                if (conversation == null) {
                    return@launch
                }
                // Resolve the TARGET conversation's own path — not messages.value, which
                // is the currently-open conversation. Same fix as generateTitle().
                val entities = convRepo.getMessagesForConversationSnapshot(conversationId)
                val path = ConversationUiState.resolvePath(
                    allMessages = entities.map {
                        ChatMessage(
                            id = it.id,
                            parentId = it.parentId,
                            text = it.text,
                            participant = it.participant,
                            timestamp = it.timestamp,
                            status = it.status,
                            modelName = it.modelName
                        )
                    },
                    streamingMsg = null,
                    selectedChildren = emptyMap()
                )

                val visibleMsgs = path.filter {
                    it.participant == Participant.USER || it.participant == Participant.MODEL
                }
                val userMsgs = visibleMsgs.filter { it.participant == Participant.USER }
                val maxContext = settings.maxContextWindow.value
                val userCountExceeded = userMsgs.size > maxContext

                val currentModelIdWithPrefix = conversation.modelId ?: settings.selectedModel.value
                val resolvedTokenLimit = resolveModelContextLimit(currentModelIdWithPrefix)
                val tokenThreshold = (resolvedTokenLimit * 0.8).toInt()
                val currentTokenUsage = visibleMsgs.sumOf { it.tokenCount }
                val tokenExceeded = currentTokenUsage >= tokenThreshold

                if (!userCountExceeded && !tokenExceeded) {
                    return@launch  // 两条都没触发，不压缩
                }

                val retainBoundaryMsg = visibleMsgs.last()
                val retainFromIndex = path.indexOfLast { it.id == retainBoundaryMsg.id }.coerceAtLeast(0)

                val watermark = conversation.compactedUpToTimestamp ?: -1L
                val toCompressAll = path.subList(0, retainFromIndex)
                    // Only USER/MODEL — tool_/result_ are derived and not useful in a summary.
                    .filter { it.participant == Participant.USER || it.participant == Participant.MODEL }
                    // Skip anything already folded into the previous summary.
                    .filter { it.timestamp > watermark }
                if (toCompressAll.isEmpty()) {
                    return@launch
                }

                onSnackbarSuspend(appContext.getString(R.string.snackbar_compressing))

                // Resolve compression model / provider / key once.
                val compressModelId = settings.autoCompressModel.value
                val modelIdWithPrefix = if (!compressModelId.isNullOrBlank()) {
                    compressModelId
                } else {
                    conversation.modelId ?: settings.selectedModel.value
                }
                val modelId = ModelId.parse(modelIdWithPrefix).modelName
                val (providerName, activeKey) = requestBuilder.resolveProviderKey(modelIdWithPrefix) ?: run {
                    onSnackbarSuspend(appContext.getString(R.string.snackbar_compress_error))
                    return@launch
                }

                val provider = providerRegistry.getInstance(providerName)
                    ?: error("Provider '$providerName' is not registered")
                val config = ProviderConfig(
                    apiKey = activeKey,
                    modelId = modelId,
                    systemPrompt = settings.autoCompressPrompt.value.ifBlank { BuiltInPrompts.HISTORY_COMPRESSION_SYSTEM },
                    maxContextWindow = 1,
                    thinkingEnabled = false,
                    baseUrl = providerRegistry.getEffectiveBaseUrl(providerName)
                )

                var runningSummary = conversation.compactedSummary
                var totalDeletedCount = 0
                var cursor = 0
                val batchTokenThreshold = (resolveModelContextLimit(modelIdWithPrefix) * 0.8).toInt()
                var currentWatermark = watermark

                while (cursor < toCompressAll.size) {
                    var batchTokens = 0
                    var batchEnd = cursor
                    // Collect at least one message, then keep adding while under the threshold.
                    while (batchEnd < toCompressAll.size) {
                        val msg = toCompressAll[batchEnd]
                        val msgTokens = msg.tokenCount
                        if (batchEnd > cursor && batchTokens + msgTokens > batchTokenThreshold) {
                            break
                        }
                        batchTokens += msgTokens
                        batchEnd++
                    }
                    if (batchEnd == cursor) {
                        batchEnd = cursor + 1
                    }

                    val batch = toCompressAll.subList(cursor, batchEnd)
                    val conversationText = batch.joinToString("\n\n") { msg ->
                        val role = if (msg.participant == Participant.USER) "User" else "Assistant"
                        val body = msg.text.take(2000)
                        "$role: $body"
                    }
                    val summaryInput = if (runningSummary != null) {
                        "[Previous summary]\n$runningSummary\n\n[New messages to fold in]\n$conversationText"
                    } else {
                        conversationText
                    }

                    val prompt = listOf(
                        ChatMessage(
                            text = "Summarize the following conversation history so the chat can continue with full context. Preserve key facts, decisions, names, and any unresolved questions.\n\n$summaryInput",
                            participant = Participant.USER,
                            status = MessageStatus.SUCCESS
                        )
                    )

                    var summary = ""
                    try {
                        // Serialize with embedding to avoid dual model load OOM (same as generateTitle).
                        if (providerName == Constants.PROVIDER_LOCAL) {
                            LlamaEngine.modelMutex.withLock {
                                withContext(Dispatchers.IO) {
                                    provider.generateResponse(prompt, config).collect { event ->
                                        if (event is StreamEvent.TextChunk) summary += event.text
                                        else if (event is StreamEvent.Error) DebugLog.e("OrangeIslandVM", "History compression error: ${event.message}")
                                    }
                                }
                                localProvider.releaseEngine()
                            }
                        } else {
                            provider.generateResponse(prompt, config).collect { event ->
                                if (event is StreamEvent.TextChunk) summary += event.text
                                else if (event is StreamEvent.Error) DebugLog.e("OrangeIslandVM", "History compression error: ${event.message}")
                            }
                        }
                    } catch (e: Exception) {
                        DebugLog.e("OrangeIslandVM", "History compression failed for provider=$providerName model=$modelId", e)
                        onSnackbarSuspend(appContext.getString(R.string.snackbar_compress_error))
                        break
                    }

                    summary = summary.trim()
                    if (summary.isBlank()) {
                        break
                    }

                    val lastBatchMsg = batch.last()

                    // Persist immediately so partial failure does not roll back prior rounds.
                    convRepo.getConversation(conversationId)?.let { existing ->
                        convRepo.upsertConversation(existing.copy(
                            compactedSummary = summary,
                            compactedUpToTimestamp = lastBatchMsg.timestamp
                        ))
                    }

                    // Delete every message in this batch plus their tool_/result_ children.
                    val idsToDelete = entities
                        .filter { it.timestamp > currentWatermark && it.timestamp <= lastBatchMsg.timestamp }
                        .map { it.id }

                    if (idsToDelete.isNotEmpty()) {
                        convRepo.deleteMessagesByIds(idsToDelete)
                        val deletedSet = idsToDelete.toHashSet()
                        val orphaned = entities.filter { it.id !in deletedSet && it.parentId in deletedSet }
                        orphaned.forEach { msg ->
                            convRepo.upsertMessage(msg.copy(parentId = null))
                        }
                        totalDeletedCount += idsToDelete.size
                    }

                    runningSummary = summary
                    currentWatermark = lastBatchMsg.timestamp
                    cursor = batchEnd
                }

                if (totalDeletedCount > 0) {
                    onSnackbarSuspend(appContext.getString(R.string.snackbar_compressed, totalDeletedCount))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.e("MessageGenerationController", "compressHistory failed for conv=$conversationId", e)
                onSnackbarSuspend(appContext.getString(R.string.snackbar_compress_error))
            } finally {
                compressingConversationIds.remove(conversationId)
            }
        }
    }
}
