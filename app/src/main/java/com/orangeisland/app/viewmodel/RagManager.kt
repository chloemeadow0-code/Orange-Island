package com.orangeisland.app.viewmodel

import android.content.Context
import com.orangeisland.app.R
import com.orangeisland.app.api.EmbeddingClient
import com.orangeisland.app.api.LlamaEngine
import com.orangeisland.app.api.ProviderDefaults
import com.orangeisland.app.api.local.LocalProvider
import com.orangeisland.app.data.EmbeddingIndexer
import com.orangeisland.app.data.EmbeddingModelConfig
import com.orangeisland.app.data.EmbeddingModelType
import com.orangeisland.app.data.local.EmbeddingEntity
import com.orangeisland.app.data.repository.ConversationRepository
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.util.SnackbarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the embedding subsystem: embedding-model CRUD, the RAG cache (per-model
 * embedding of all messages), single-message indexing, and embedding key/base-URL
 * resolution.
 *
 * Extracted out of [ChatViewModel] (Phase E4). The whole subsystem moves together
 * because embedding-model deletion and caching coordinate on the same per-model
 * lock ([cacheMutexes]) and cancellation handle ([cacheJobs]). ChatViewModel keeps
 * thin delegating wrappers for the UI-facing API.
 */
class RagManager(
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
    private val localProvider: LocalProvider,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val emitSnackbar: suspend (SnackbarEvent) -> Unit,
) {
    val activeEmbeddingModel: StateFlow<EmbeddingModelConfig?> =
        combine(settings.embeddingModels, settings.activeEmbeddingModelId) { models, id ->
            models.find { it.id == id }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _cachingProgress = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val cachingProgress: StateFlow<Map<String, Pair<Int, Int>>> = _cachingProgress.asStateFlow()
    private val cacheMutexes = ConcurrentHashMap<String, Mutex>()
    // In-app caching coroutine per model, so deleteEmbeddingModel can cancel an
    // in-flight cache instead of queueing behind it on the mutex.
    private val cacheJobs = ConcurrentHashMap<String, Job>()
    private val _cacheCounts = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val cacheCounts: StateFlow<Map<String, Pair<Int, Int>>> = _cacheCounts.asStateFlow()

    fun loadCacheCounts() {
        scope.launch(Dispatchers.IO) { refreshCacheCounts() }
    }

    private suspend fun refreshCacheCounts() {
        val total = conversations.getIndexableMessageCount()
        val counts = settings.embeddingModels.value.associate { model ->
            val cached = conversations.getEmbeddingCountByModel(model.id).coerceAtMost(total)
            model.id to (cached to total)
        }
        _cacheCounts.value = counts
    }

    // ── Embedding-model CRUD ──────────────────────────────────────

    fun addEmbeddingModel(config: EmbeddingModelConfig) {
        scope.launch {
            val wasEmpty = settings.embeddingModels.value.isEmpty()
            val models = settings.embeddingModels.value.toMutableList()
            models.add(config)
            settings.saveEmbeddingModels(models)
            if (wasEmpty) {
                settings.setActiveEmbeddingModelId(config.id)
            }
            refreshCacheCounts()
        }
    }

    fun deleteEmbeddingModel(id: String) {
        // Stop the background WorkManager cache job for this model right away. cancel()
        // is async, so we await termination below before deleting rows — otherwise a
        // worker batch in flight would re-insert embeddings for the now-deleted model.
        val workManager = androidx.work.WorkManager.getInstance(appContext)
        val workName = com.orangeisland.app.service.EmbeddingCacheWorker.workNameFor(id)
        workManager.cancelUniqueWork(workName)

        scope.launch(Dispatchers.IO) {
            // Stop the in-app caching coroutine and wait for it to fully unwind (it
            // holds cacheMutexes[id] for its whole loop, so cancel+join — not the lock —
            // is what actually halts it before we take the mutex ourselves).
            cacheJobs.remove(id)?.let { it.cancel(); it.join() }

            // Deterministically wait until the worker has reached a finished state
            // (CANCELLED/SUCCEEDED/FAILED) so no writer remains. Empty info list (work
            // never existed) satisfies the predicate immediately. Bounded so a stuck
            // worker can't hang deletion.
            withTimeoutOrNull(10_000) {
                workManager.getWorkInfosForUniqueWorkFlow(workName)
                    .first { infos -> infos.all { it.state.isFinished } }
            }

            val mutex = cacheMutexes.computeIfAbsent(id) { Mutex() }
            mutex.withLock {
                val model = settings.embeddingModels.value.find { it.id == id }
                if (model?.type == EmbeddingModelType.LOCAL && model.localFilePath.isNotBlank()) {
                    java.io.File(model.localFilePath).delete()
                }
                conversations.deleteEmbeddingsByModel(id)
                val models = settings.embeddingModels.value.filter { it.id != id }
                settings.saveEmbeddingModels(models)
                if (settings.activeEmbeddingModelId.value == id && models.isNotEmpty()) {
                    settings.setActiveEmbeddingModelId(models.first().id)
                }
                _cachingProgress.update { it - id }
                refreshCacheCounts()
            }
            cacheMutexes.remove(id)
        }
    }

    fun renameEmbeddingModel(id: String, newName: String, batchSize: Int? = null) {
        scope.launch {
            val models = settings.embeddingModels.value.map {
                if (it.id == id) it.copy(name = newName, batchSize = batchSize ?: it.batchSize) else it
            }
            settings.saveEmbeddingModels(models)
        }
    }

    fun setActiveEmbeddingModel(id: String) {
        if (id == settings.activeEmbeddingModelId.value) return
        scope.launch(Dispatchers.IO) {
            settings.setActiveEmbeddingModelId(id)
            val model = settings.embeddingModels.value.find { it.id == id } ?: return@launch
            val total = conversations.getAllMessagesForIndexing().count { it.text.isNotBlank() }
            val cached = conversations.getEmbeddingCountByModel(id)
            val notCached = (total - cached).coerceAtLeast(0)
            if (notCached > 0) {
                if (cachingProgress.value.containsKey(id)) {
                    emitSnackbar(SnackbarEvent(appContext.getString(R.string.embedding_model_caching, model.name)))
                } else {
                    emitSnackbar(SnackbarEvent(
                        appContext.getString(R.string.messages_not_cached, notCached, total),
                        appContext.getString(R.string.cache_now)
                    ) { cacheMessagesForModel(id) })
                }
            }
        }
    }

    // ── RAG cache ─────────────────────────────────────────────────

    fun cacheMessagesForModel(modelId: String, recache: Boolean = false, silent: Boolean = false) {
        // Enqueue WorkManager backup — survives process death if user leaves app
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.orangeisland.app.service.EmbeddingCacheWorker>()
            .setInputData(androidx.work.Data.Builder()
                .putString(com.orangeisland.app.service.EmbeddingCacheWorker.KEY_MODEL_ID, modelId)
                .build())
            .addTag(com.orangeisland.app.service.EmbeddingCacheWorker.TAG)
            .build()
        androidx.work.WorkManager.getInstance(appContext)
            .enqueueUniqueWork(
                com.orangeisland.app.service.EmbeddingCacheWorker.workNameFor(modelId),
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )

        val job = scope.launch(Dispatchers.IO) {
            val mutex = cacheMutexes.computeIfAbsent(modelId) { Mutex() }
            mutex.withLock {
                val model = settings.embeddingModels.value.find { it.id == modelId } ?: return@launch
                if (recache) {
                    conversations.deleteEmbeddingsByModel(modelId)
                }
                val allMessages = conversations.getAllMessagesForIndexing().filter { it.text.isNotBlank() }
                val total = allMessages.size
                if (total == 0) {
                    if (!silent) emitSnackbar(SnackbarEvent(appContext.getString(R.string.no_messages_to_cache)))
                    refreshCacheCounts()
                    return@launch
                }
                val existingIds = conversations.getEmbeddedMessageIdsByModel(modelId).toSet()
                val toProcess = allMessages.filter { it.id !in existingIds }
                if (toProcess.isEmpty()) {
                    if (!silent) emitSnackbar(SnackbarEvent(appContext.getString(R.string.all_messages_already_cached, total)))
                    refreshCacheCounts()
                    return@launch
                }
                val alreadyDone = total - toProcess.size
                var succeeded = 0
                var attempted = 0
                _cachingProgress.update { it + (modelId to (alreadyDone to total)) }
                try {
                    val batchSize = model.batchSize.coerceIn(1, 100)
                    if (model.type == EmbeddingModelType.LOCAL) {
                        if (!LlamaEngine.isModelReady(model.localFilePath)) {
                            if (!silent) emitSnackbar(SnackbarEvent(appContext.getString(R.string.local_model_not_found)))
                            return@launch
                        }
                        toProcess.chunked(batchSize).forEach { batch ->
                            if (settings.embeddingModels.value.none { it.id == modelId }) return@launch
                            val texts = batch.map { it.text.take(Constants.MAX_EMBEDDING_TEXT_LENGTH) }
                            val embeddings = LlamaEngine.computeEmbeddings(texts, model.localFilePath) {
                                localProvider.releaseEngineBlocking()
                            }
                            batch.zip(embeddings).forEach { (msg, embd) ->
                                attempted++
                                if (embd != null) {
                                    conversations.upsertEmbedding(EmbeddingEntity(
                                        messageId = msg.id, modelId = modelId,
                                        embedding = EmbeddingIndexer.floatsToBytes(embd),
                                        chunkText = msg.text.take(Constants.MAX_CHUNK_TEXT_LENGTH), dimension = embd.size
                                    ))
                                    succeeded++
                                }
                            }
                            _cachingProgress.update { it + (modelId to (alreadyDone + attempted to total)) }
                        }
                    } else {
                        val apiKey = model.remoteApiKey.ifBlank { resolveEmbeddingApiKey() ?: "" }
                        if (apiKey.isBlank()) {
                            if (!silent) emitSnackbar(SnackbarEvent(appContext.getString(R.string.no_api_key_configured)))
                            return@launch
                        }
                        val baseUrl = model.remoteBaseUrl.ifBlank { resolveEmbeddingBaseUrl() }
                        toProcess.chunked(batchSize).forEach { batch ->
                            if (settings.embeddingModels.value.none { it.id == modelId }) return@launch
                            val texts = batch.map { it.text.take(Constants.MAX_EMBEDDING_TEXT_LENGTH) }
                            val embeddings = EmbeddingClient.computeEmbeddings(
                                texts, apiKey, model.remoteModelName, baseUrl
                            )
                            batch.zip(embeddings).forEach { (msg, embd) ->
                                attempted++
                                if (embd != null) {
                                    conversations.upsertEmbedding(EmbeddingEntity(
                                        messageId = msg.id, modelId = modelId,
                                        embedding = EmbeddingIndexer.floatsToBytes(embd),
                                        chunkText = msg.text.take(Constants.MAX_CHUNK_TEXT_LENGTH), dimension = embd.size
                                    ))
                                    succeeded++
                                }
                            }
                            _cachingProgress.update { it + (modelId to (alreadyDone + attempted to total)) }
                        }
                    }
                } finally {
                    _cachingProgress.update { it - modelId }
                }
                val failed = toProcess.size - succeeded
                if (!silent) {
                    if (failed == 0) {
                        emitSnackbar(SnackbarEvent(appContext.getString(R.string.all_messages_cached, total)))
                    } else {
                        emitSnackbar(SnackbarEvent(
                            appContext.getString(R.string.cached_partial_failed, succeeded, toProcess.size, failed),
                            appContext.getString(R.string.retry)
                        ) { cacheMessagesForModel(modelId) })
                    }
                }
                conversations.deleteOrphanedEmbeddings()
                refreshCacheCounts()
            }
        }
        // Track the job so deleteEmbeddingModel can cancel an in-flight cache; self-remove
        // on completion (guard against clobbering a newer job for the same model).
        cacheJobs[modelId] = job
        job.invokeOnCompletion { cacheJobs.remove(modelId, job) }
    }

    // ── Single-message indexing ───────────────────────────────────

    fun indexMessageForRag(messageId: String, text: String) {
        scope.launch(Dispatchers.IO) {
            val model = activeEmbeddingModel.value
            if (model == null) {
                DebugLog.d("OrangeIslandVM", "RAG index: no active model, skipping $messageId")
                return@launch
            }
            DebugLog.d("OrangeIslandVM", "RAG index: indexing $messageId with model '${model.name}'")
            val toEmbed = text.take(Constants.MAX_EMBEDDING_TEXT_LENGTH)
            val embedding: FloatArray? = if (model.type == EmbeddingModelType.LOCAL) {
                if (!LlamaEngine.isModelReady(model.localFilePath)) {
                    DebugLog.w("OrangeIslandVM", "RAG index: local model not ready, skipping")
                    return@launch
                }
                LlamaEngine.computeEmbedding(toEmbed, model.localFilePath) {
                    localProvider.releaseEngineBlocking()
                }
            } else {
                val apiKey = model.remoteApiKey.ifBlank { resolveEmbeddingApiKey() ?: "" }
                if (apiKey.isBlank()) {
                    DebugLog.w("OrangeIslandVM", "RAG index: no API key, skipping")
                    return@launch
                }
                val baseUrl = model.remoteBaseUrl.ifBlank { resolveEmbeddingBaseUrl() }
                EmbeddingClient.computeEmbedding(toEmbed, apiKey, model.remoteModelName, baseUrl)
            }
            if (embedding != null) {
                conversations.upsertEmbedding(EmbeddingEntity(
                    messageId = messageId,
                    modelId = model.id,
                    embedding = EmbeddingIndexer.floatsToBytes(embedding),
                    chunkText = text.take(Constants.MAX_CHUNK_TEXT_LENGTH),
                    dimension = embedding.size
                ))
                DebugLog.d("OrangeIslandVM", "RAG index: stored embedding (dim=${embedding.size}) for $messageId")
            }
        }
    }

    // ── Embedding key / base-URL resolution ───────────────────────

    fun resolveEmbeddingApiKey(): String? {
        val keys = settings.apiKeys.value
        for (entry in keys) {
            if (ProviderDefaults.isOpenAiCompatibleEmbedding(entry.provider)) {
                return entry.key
            }
        }
        return keys.firstOrNull()?.key
    }

    fun resolveEmbeddingBaseUrl(): String {
        return ProviderDefaults.openAiCompatibleBaseUrl(settings.providerBaseUrls.value)
    }

    data class EmbeddingKeyInfo(val provider: String, val key: String, val baseUrl: String)

    /** Exact match only — for UI display in the embedding dialog. No fallback. */
    fun resolveEmbeddingKeyForProviderExact(targetProvider: String): EmbeddingKeyInfo? {
        val keys = settings.apiKeys.value
        val match = keys.find { it.provider.equals(targetProvider, ignoreCase = true) }
        if (match != null) {
            val baseUrl = settings.providerBaseUrls.value[match.provider] ?: ProviderDefaults.embeddingBaseUrl(match.provider)
            return EmbeddingKeyInfo(match.provider, match.key, baseUrl)
        }
        return null
    }
}
