package com.orangeisland.app.ui.chat

import android.webkit.JavascriptInterface
import com.orangeisland.app.util.DebugLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JavaScript bridge injected into the Mini App WebView so the web layer can read
 * App-native state (chat memories, conversation history, app context) and send
 * events back into the native layer.
 *
 * The bridge object is exposed to JS as `OrangeIslandBridge`.
 *
 * Example JS usage:
 * ```js
 * // Read current conversation messages
 * const history = JSON.parse(OrangeIslandBridge.getChatHistory());
 * // Read long-term memories
 * const memories = JSON.parse(OrangeIslandBridge.getLongTermMemories());
 * // Send a message back to the App
 * OrangeIslandBridge.postMessage(JSON.stringify({ action: 'send_chat', text: 'Hello' }));
 * ```
 */
class MiniAppJsBridge(
    /** Snapshot of the current conversation messages (JSON). */
    private val chatHistoryJson: () -> String,
    /** Current conversation id, or empty string if none. */
    private val currentConversationId: () -> String,
    /** Long-term memory entries JSON. */
    private val longTermMemoriesJson: () -> String,
    /** App-level context string (theme, locale, battery, etc.). */
    private val appContextJson: () -> String,
    /** Callback from the web page → native. */
    private val onMessageFromWeb: (String) -> Unit,
) {

    companion object {
        /** Global JS interface name visible in window. */
        const val JS_INTERFACE_NAME = "OrangeIslandBridge"
    }

    @JavascriptInterface
    fun getChatHistory(): String {
        return try {
            chatHistoryJson()
        } catch (e: Exception) {
            DebugLog.e("MiniAppBridge", "getChatHistory failed", e)
            "[]"
        }
    }

    @JavascriptInterface
    fun getCurrentConversationId(): String {
        return currentConversationId()
    }

    @JavascriptInterface
    fun getLongTermMemories(): String {
        return try {
            longTermMemoriesJson()
        } catch (e: Exception) {
            DebugLog.e("MiniAppBridge", "getLongTermMemories failed", e)
            "[]"
        }
    }

    @JavascriptInterface
    fun getAppContext(): String {
        return try {
            appContextJson()
        } catch (e: Exception) {
            DebugLog.e("MiniAppBridge", "getAppContext failed", e)
            "{}"
        }
    }

    /**
     * Entry-point for the web page to send arbitrary JSON payloads back to the native layer.
     * The native host should route the payload based on an `action` field.
     */
    @JavascriptInterface
    fun postMessage(jsonPayload: String) {
        DebugLog.d("MiniAppBridge", "postMessage: ${jsonPayload.take(200)}")
        onMessageFromWeb(jsonPayload)
    }

    /** Helper to build the bootstrap script that the WebView injects on every page load. */
    fun buildBootstrapScript(): String {
        return """
            (function() {
                // Notify the web page that the bridge is ready.
                window.dispatchEvent(new CustomEvent('OrangeIslandBridgeReady', {
                    detail: { conversationId: '${escapeJs(currentConversationId())}' }
                }));
            })();
        """.trimIndent()
    }

    private fun escapeJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
