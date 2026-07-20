package com.orangeisland.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.orangeisland.app.api.EmbeddingClient
import com.orangeisland.app.api.LlamaEngine
import com.orangeisland.app.api.ProviderDefaults
import com.orangeisland.app.data.EmbeddingIndexer
import com.orangeisland.app.data.EmbeddingModelConfig
import com.orangeisland.app.data.EmbeddingModelType
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.data.local.EmbeddingEntity
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that caches embeddings for a given model.
 *
 * Survives process death — if the user leaves the app during caching,
 * the worker continues in the background. Progress is reported via
 * WorkManager's [setProgress].
 *
 * Input data: "model_id" (String) — the embedding model ID to cache.
 * Output data: "cached" (Int), "total" (Int), "failed" (Int).
 */
class EmbeddingCacheWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_CACHED = "cached"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
        const val TAG = "EmbeddingCache"

        /** Enqueue rule: only one cache job per model at a time. */
        fun workNameFor(modelId: String) = "embedding_cache_$modelId"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
        if (modelId.isNullOrBlank()) {
            DebugLog.w(TAG, "No model_id in input data")
            return@withContext Result.failure()
        }

        val db = ChatDatabase.build(applicationContext)
        val chatDao = db.chatDao()
        val settingsManager = SettingsManager(applicationContext)
        val models = settingsManager.embeddingModels.first()
        val model = models.find { it.id == modelId }
        if (model == null) {
            DebugLog.w(TAG, "Model $modelId not found")
            return@withContext Result.failure()
        }

        // Check cancellation
        if (isStopped) return@withContext Result.failure()

        val allMessages = chatDao.getAllMessagesForIndexing().filter { it.text.isNotBlank() }
        val total = allMessages.size
        if (total == 0) {
            return@withContext Result.success(Data.Builder()
                .putInt(KEY_CACHED, 0).putInt(KEY_TOTAL, 0).putInt(KEY_FAILED, 0).build())
        }

        val existingIds = chatDao.getEmbeddedMessageIdsByModel(modelId).toSet()
        val toProcess = allMessages.filter { it.id !in existingIds }
        if (toProcess.isEmpty()) {
            return@withContext Result.success(Data.Builder()
                .putInt(KEY_CACHED, total).putInt(KEY_TOTAL, total).putInt(KEY_FAILED, 0).build())
        }

        val alreadyDone = total - toProcess.size
        var succeeded = 0
        var attempted = 0
        val batchSize = model.batchSize.coerceIn(1, 100)

        try {
            setProgress(workDataOf(KEY_CACHED to alreadyDone, KEY_TOTAL to total))

            if (model.type == EmbeddingModelType.LOCAL) {
                if (!LlamaEngine.isModelReady(model.localFilePath)) {
                    return@withContext Result.failure(Data.Builder()
                        .putString("error", "Local model file not found").build())
                }
                toProcess.chunked(batchSize).forEach { batch ->
                    if (isStopped) return@withContext Result.failure()
                    val texts = batch.map { it.text.take(Constants.MAX_EMBEDDING_TEXT_LENGTH) }
                    val embeddings = LlamaEngine.computeEmbeddings(texts, model.localFilePath)
                    batch.zip(embeddings).forEach { (msg, embd) ->
                        attempted++
                        if (embd != null) {
                            chatDao.upsertEmbedding(EmbeddingEntity(
                                messageId = msg.id, modelId = modelId,
                                embedding = EmbeddingIndexer.floatsToBytes(embd),
                                chunkText = msg.text.take(Constants.MAX_CHUNK_TEXT_LENGTH),
                                dimension = embd.size
                            ))
                            succeeded++
                        }
                    }
                    setProgress(workDataOf(KEY_CACHED to alreadyDone + attempted, KEY_TOTAL to total))
                }
            } else {
                val apiKey = model.remoteApiKey.ifBlank { resolveApiKey(settingsManager) ?: "" }
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Data.Builder()
                        .putString("error", "No API key configured").build())
                }
                val baseUrl = model.remoteBaseUrl.ifBlank { resolveBaseUrl(settingsManager) }
                toProcess.chunked(batchSize).forEach { batch ->
                    if (isStopped) return@withContext Result.failure()
                    val texts = batch.map { it.text.take(Constants.MAX_EMBEDDING_TEXT_LENGTH) }
                    val embeddings = EmbeddingClient.computeEmbeddings(
                        texts, apiKey, model.remoteModelName, baseUrl
                    )
                    batch.zip(embeddings).forEach { (msg, embd) ->
                        attempted++
                        if (embd != null) {
                            chatDao.upsertEmbedding(EmbeddingEntity(
                                messageId = msg.id, modelId = modelId,
                                embedding = EmbeddingIndexer.floatsToBytes(embd),
                                chunkText = msg.text.take(Constants.MAX_CHUNK_TEXT_LENGTH),
                                dimension = embd.size
                            ))
                            succeeded++
                        }
                    }
                    setProgress(workDataOf(KEY_CACHED to alreadyDone + attempted, KEY_TOTAL to total))
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Cache worker failed", e)
            return@withContext Result.failure(Data.Builder()
                .putString("error", e.localizedMessage ?: "Unknown error").build())
        }

        val failed = toProcess.size - succeeded
        DebugLog.d(TAG, "Cache complete: $succeeded/$total cached, $failed failed")
        return@withContext Result.success(Data.Builder()
            .putInt(KEY_CACHED, alreadyDone + succeeded)
            .putInt(KEY_TOTAL, total)
            .putInt(KEY_FAILED, failed)
            .build())
    }

    private suspend fun resolveApiKey(settingsManager: SettingsManager): String? {
        val keys = settingsManager.apiKeys.first()
        for (entry in keys) {
            if (ProviderDefaults.isOpenAiCompatibleEmbedding(entry.provider)) {
                return entry.key
            }
        }
        return keys.firstOrNull()?.key
    }

    private suspend fun resolveBaseUrl(settingsManager: SettingsManager): String {
        return ProviderDefaults.openAiCompatibleBaseUrl(settingsManager.providerBaseUrls.first())
    }
}
