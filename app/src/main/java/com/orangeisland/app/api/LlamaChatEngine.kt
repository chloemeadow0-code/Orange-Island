package com.orangeisland.app.api

import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File

interface NativeChatCallback {
    fun onToken(token: String)
    fun onDone()
    fun onError(message: String)
}

class ChatTemplateMessage(val role: String, val content: String)

class LlamaChatEngine(
    val modelPath: String,
    val nCtx: Int = 2048
) : Closeable {
    companion object {
        private const val TAG = "LlamaChatEngine"

        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("orangeisland_llama")
        }
    }

    @Volatile
    private var nativeHandle: Long = 0

    private external fun nativeChatLoadModel(path: String, nCtx: Int): Long
    private external fun nativeChatGetTemplate(handle: Long): String?
    private external fun nativeChatApplyTemplate(handle: Long, messages: Array<ChatTemplateMessage>, addAss: Boolean): String?
    private external fun nativeChatLoadMmproj(handle: Long, mmprojPath: String): Boolean
    private external fun nativeChatUnloadMmproj(handle: Long)
    private external fun nativeChatHasMmproj(handle: Long): Boolean
    private external fun nativeChatGenerateWithImages(
        handle: Long, prompt: String, imagePaths: Array<String>,
        temperature: Float, topP: Float, maxTokens: Int, callback: NativeChatCallback
    ): Int
    private external fun nativeChatGenerate(
        handle: Long, prompt: String, temperature: Float, topP: Float, maxTokens: Int,
        callback: NativeChatCallback
    ): Int
    private external fun nativeChatReset(handle: Long)
    private external fun nativeChatFreeModel(handle: Long)
    private external fun nativeChatCancel(handle: Long)

    fun isLoaded(): Boolean = nativeHandle != 0L

    fun load(): Boolean {
        if (!File(modelPath).exists()) {
            DebugLog.e(TAG, "Model file not found: $modelPath")
            return false
        }
        nativeHandle = nativeChatLoadModel(modelPath, nCtx)
        if (nativeHandle == 0L) {
            DebugLog.e(TAG, "Failed to load model: $modelPath")
            return false
        }
        DebugLog.d(TAG, "Model loaded: $modelPath, nCtx=$nCtx")
        return true
    }

    fun getChatTemplate(): String? {
        if (nativeHandle == 0L) return null
        return nativeChatGetTemplate(nativeHandle)
    }

    fun applyTemplate(
        messages: List<ChatTemplateMessage>,
        addAss: Boolean = true
    ): String? {
        if (nativeHandle == 0L) return null
        return nativeChatApplyTemplate(nativeHandle, messages.toTypedArray(), addAss)
    }

    fun generate(
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        maxTokens: Int = 4096
    ): Flow<String> = callbackFlow {
        if (nativeHandle == 0L) {
            close(RuntimeException("Model not loaded"))
            return@callbackFlow
        }

        val callback = object : NativeChatCallback {
            override fun onToken(token: String) {
                trySend(token)
            }

            override fun onDone() {
                close()
            }

            override fun onError(message: String) {
                DebugLog.e(TAG, "Generation error: $message")
                close(RuntimeException(message))
            }
        }

        launch(Dispatchers.IO) {
            try {
                nativeChatGenerate(nativeHandle, prompt, temperature, topP, maxTokens, callback)
            } catch (e: Exception) {
                DebugLog.e(TAG, "nativeChatGenerate crashed", e)
                close(e)
            }
        }

        awaitClose {
            synchronized(this@LlamaChatEngine) {
                if (nativeHandle != 0L) {
                    nativeChatCancel(nativeHandle)
                }
            }
        }
    }

    fun loadMmproj(mmprojPath: String): Boolean {
        synchronized(this) {
            if (nativeHandle == 0L) return false
            if (!File(mmprojPath).exists()) {
                DebugLog.e(TAG, "mmproj file not found: $mmprojPath")
                return false
            }
            return nativeChatLoadMmproj(nativeHandle, mmprojPath)
        }
    }

    fun hasMmproj(): Boolean {
        synchronized(this) {
            return nativeHandle != 0L && nativeChatHasMmproj(nativeHandle)
        }
    }

    fun unloadMmproj() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                nativeChatUnloadMmproj(nativeHandle)
            }
        }
    }

    fun generateWithImages(
        prompt: String,
        imagePaths: List<String>,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        maxTokens: Int = 4096
    ): Flow<String> = callbackFlow {
        if (nativeHandle == 0L) {
            close(RuntimeException("Model not loaded"))
            return@callbackFlow
        }

        val callback = object : NativeChatCallback {
            override fun onToken(token: String) { trySend(token) }
            override fun onDone() { close() }
            override fun onError(message: String) {
                DebugLog.e(TAG, "Generation error: $message")
                close(RuntimeException(message))
            }
        }

        launch(Dispatchers.IO) {
            try {
                nativeChatGenerateWithImages(
                    nativeHandle, prompt, imagePaths.toTypedArray(),
                    temperature, topP, maxTokens, callback
                )
            } catch (e: Exception) {
                DebugLog.e(TAG, "nativeChatGenerateWithImages crashed", e)
                close(e)
            }
        }

        awaitClose {
            synchronized(this@LlamaChatEngine) {
                if (nativeHandle != 0L) nativeChatCancel(nativeHandle)
            }
        }
    }

    fun cancel() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                nativeChatCancel(nativeHandle)
            }
        }
    }

    fun resetContext() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                nativeChatReset(nativeHandle)
            }
        }
    }

    override fun close() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                nativeChatFreeModel(nativeHandle)
                nativeHandle = 0L
                DebugLog.d(TAG, "Model closed: $modelPath")
            }
        }
    }

    protected fun finalize() {
        close()
    }
}
