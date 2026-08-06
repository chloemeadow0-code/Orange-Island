package com.orangeisland.app.ui.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.plugin.OrangeIslandWebViewBridge
import com.orangeisland.app.plugin.PluginMemoryProvider
import com.orangeisland.app.plugin.PluginSandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Renders a plugin's `ui.html` inside a sandboxed WebView and wires up the `orangeisland` JS bridge
 * so the page can invoke the plugin's own tools via `orangeisland.call(tool, args, cb)`.
 *
 * When [memoryProvider] is supplied, the bridge also exposes `orangeisland.getChatHistory`,
 * `orangeisland.getLongTermMemories`, `orangeisland.getActiveMemory`, and
 * `orangeisland.sendChatMessage` so the plugin UI can read/write the host app's chat context
 * with full project isolation.
 *
 * Security configuration (matches [com.orangeisland.app.plugin.PluginSandbox] philosophy):
 *  - `allowFileAccess = false`, `allowContentAccess = false` — no host filesystem access
 *  - `loadDataWithBaseURL("about:blank", ...)` — page can't navigate to remote URLs
 *  - `javaScriptCanOpenWindowsAutomatically = false`, no `addJavascriptInterface` other than
 *    the [OrangeIslandWebViewBridge], which only permits tool names in the plugin's manifest
 *
 * Lifecycle: the bridge is created in `remember` tied to [plugin] and torn down via
 * [DisposableEffect] when the composable leaves the tree, ensuring the WebView and its
 * bridge coroutine are released promptly (so timers / fetch loops in the page stop).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginWebViewPage(
    plugin: InstalledPlugin,
    sandbox: PluginSandbox,
    onBack: () -> Unit,
    memoryProvider: PluginMemoryProvider? = null,
    /** The host's currently-active conversation id, so the plugin UI can send messages/
     *  invitations to the conversation the user has open even though the page itself is launched
     *  from Settings (outside any LLM tool-call context). May be "" if no conversation is active. */
    currentConversationId: String = "",
) {
    val uiFile = plugin.uiHtmlFile
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val appContext = ctx.applicationContext
    // Read host values SYNCHRONOUSLY from the eagerly-shared StateFlows. These are hot flows whose
    // `.value` is always current after DataStore has loaded (which happens early in app startup —
    // by the time the user navigates here, the values are populated). The previous async
    // LaunchedEffect approach raced WebView page init: the page called native.getConfig() before
    // the coroutine had a chance to populate bridge.pluginConfigJson, so it got "{}".
    //
    // We read these via the sandbox providers to stay decoupled from SettingsRepository here, but
    // fall back to the providers' suspend get() is too late — so we resolve them once now, before
    // building the bridge, by reading from the sandbox's identityProvider/configProvider via a
    // blocking runBlocking on the IO dispatcher. (Acceptable: this composable runs on the main
    // thread, the reads are fast, and we offload to IO.)
    val bridge = remember(plugin.id) {
        val deviceId = runCatching {
            kotlinx.coroutines.runBlocking {
                sandbox.identityProvider?.get() ?: ""
            }
        }.getOrDefault("")
        val configJson = runCatching {
            kotlinx.coroutines.runBlocking {
                sandbox.configProvider?.get(plugin.id) ?: "{}"
            }
        }.getOrDefault("{}")
        OrangeIslandWebViewBridge(
            plugin, sandbox, scope,
            deviceUserId = deviceId,
            pluginConfigJson = configJson,
            currentConversationId = currentConversationId,
            memoryProvider = memoryProvider,
            mediaInfoProvider = { pkg -> com.orangeisland.app.plugin.MediaInfoReader.read(appContext, pkg) }
        )
    }

    // Local text-file picker (.txt / .md) requested by the plugin page. The bridge stores the
    // callback id; we observe it here and launch the system document picker, then deliver the
    // UTF-8 file contents back to the page as JSON { name, content, mimeType }.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            bridge.deliverFilePickResult(buildJsonObject {
                put("error", "cancelled")
                put("message", "No file selected")
            }.toString())
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val result = try {
                val mimeType = ctx.contentResolver.getType(uri) ?: ""
                val supported = mimeType == "text/plain" ||
                    mimeType == "text/markdown" ||
                    mimeType == "text/x-markdown"
                if (!supported) {
                    buildJsonObject {
                        put("error", "invalid_type")
                        put("message", "Only .txt and .md files are supported (got $mimeType)")
                    }.toString()
                } else {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val content = input.bufferedReader(Charsets.UTF_8).readText()
                        val name = uri.lastPathSegment ?: "unknown"
                        buildJsonObject {
                            put("name", name)
                            put("content", content)
                            put("mimeType", mimeType)
                        }.toString()
                    } ?: buildJsonObject {
                        put("error", "read_error")
                        put("message", "Could not open input stream")
                    }.toString()
                }
            } catch (e: Exception) {
                buildJsonObject {
                    put("error", "read_error")
                    put("message", e.message ?: "unknown error")
                }.toString()
            }
            withContext(Dispatchers.Main) {
                bridge.deliverFilePickResult(result)
            }
        }
    }

    LaunchedEffect(bridge.pendingFilePickCallbackId) {
        if (bridge.pendingFilePickCallbackId != null) {
            runCatching {
                filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown"))
            }.onFailure {
                bridge.deliverFilePickResult(buildJsonObject {
                    put("error", "launcher_error")
                    put("message", it.message ?: "unknown error")
                }.toString())
            }
        }
    }

    // Load the HTML verbatim with the bootstrap prepended as a <script> tag. The bootstrap MUST
    // be wrapped in <script>...</script> — otherwise (bare JS text before <!DOCTYPE>) the WebView
    // renders it as visible body text. The bootstrap installs `orangeisland.config` /
    // `orangeisland.deviceId` getters (reading live from the bridge via @JavascriptInterface) and
    // mirrors them as the __OI_* globals.
    val rawHtml = remember(uiFile?.absolutePath, uiFile?.lastModified()) {
        val body = if (uiFile != null && uiFile.exists()) uiFile.readText() else "<h1>UI file missing</h1>"
        "<script>\n" + bridge.bootstrapScript + "\n</script>\n" + body
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plugin.manifest.name.ifBlank { plugin.id }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    // Lock down host access — the page only talks to the orangeisland bridge.
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.domStorageEnabled = false
                    settings.databaseEnabled = false
                    // Required for the bootstrap + page JS to log and for alert() to not hang.
                    webChromeClient = WebChromeClient()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    addJavascriptInterface(bridge, OrangeIslandWebViewBridge.bridgeObjectName)
                    bridge.webView = this
                    loadDataWithBaseURL("about:blank", rawHtml, "text/html", "utf-8", null)
                }
            },
        )
    }

    // Release the WebView + bridge scope on exit.
    DisposableEffect(plugin.id) {
        onDispose { bridge.close() }
    }
}

/** Encodes [s] as a JS string literal (double-quoted, JSON-escaped). */
private fun jsStringLiteral(s: String): String =
    kotlinx.serialization.json.JsonPrimitive(s).toString()
