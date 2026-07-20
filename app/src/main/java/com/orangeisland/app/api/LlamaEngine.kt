package com.orangeisland.app.api

import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object LlamaEngine {
    private const val TAG = "LlamaEngine"
    val modelMutex = Mutex()

    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("orangeisland_llama")
    }

    private external fun nativeLoadModel(path: String): Long
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeComputeEmbedding(handle: Long, text: String): FloatArray?
    private external fun nativeGetEmbeddingDim(handle: Long): Int

    fun isModelReady(modelPath: String): Boolean {
        return modelPath.isNotBlank() && File(modelPath).exists() && File(modelPath).length() > 0
    }

    fun computeEmbedding(text: String, modelPath: String, beforeLoad: (() -> Unit)? = null): FloatArray? {
        val results = computeEmbeddings(listOf(text), modelPath, beforeLoad)
        return results.firstOrNull()
    }

    fun computeEmbeddings(texts: List<String>, modelPath: String, beforeLoad: (() -> Unit)? = null): List<FloatArray?> {
        if (texts.isEmpty()) return emptyList()
        return runBlocking {
            modelMutex.withLock {
                beforeLoad?.invoke()
                val start = System.currentTimeMillis()
                val handle = nativeLoadModel(modelPath)
                if (handle == 0L) {
                    DebugLog.e(TAG, "Failed to load model (${System.currentTimeMillis() - start}ms): $modelPath")
                    return@runBlocking texts.map { null }
                }
                DebugLog.d(TAG, "Model loaded in ${System.currentTimeMillis() - start}ms, dim=${nativeGetEmbeddingDim(handle)}, processing ${texts.size} texts")
                try {
                    texts.mapIndexed { i, text ->
                        try {
                            val embd = nativeComputeEmbedding(handle, text)
                            if (embd == null) {
                                DebugLog.e(TAG, "nativeComputeEmbedding returned null for text len=${text.length} (${i+1}/${texts.size})")
                            }
                            embd
                        } catch (e: Exception) {
                            DebugLog.e(TAG, "Embedding computation crashed for text ${i+1}/${texts.size}", e)
                            null
                        }
                    }
                } finally {
                    nativeFreeModel(handle)
                    DebugLog.d(TAG, "Batch complete: ${texts.size} texts in ${System.currentTimeMillis() - start}ms")
                }
            }
        }
    }
}
