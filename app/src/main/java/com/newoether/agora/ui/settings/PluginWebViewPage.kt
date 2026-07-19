package com.newoether.agora.ui.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.newoether.agora.data.InstalledPlugin
import com.newoether.agora.plugin.AgoraWebViewBridge
import com.newoether.agora.plugin.PluginSandbox

/**
 * Renders a plugin's `ui.html` inside a sandboxed WebView and wires up the `agora` JS bridge
 * so the page can invoke the plugin's own tools via `agora.call(tool, args, cb)`.
 *
 * Security configuration (matches [com.newoether.agora.plugin.PluginSandbox] philosophy):
 *  - `allowFileAccess = false`, `allowContentAccess = false` — no host filesystem access
 *  - `loadDataWithBaseURL("about:blank", ...)` — page can't navigate to remote URLs
 *  - `javaScriptCanOpenWindowsAutomatically = false`, no `addJavascriptInterface` other than
 *    the [AgoraWebViewBridge], which only permits tool names in the plugin's manifest
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
) {
    val uiFile = plugin.uiHtmlFile
    val scope = rememberCoroutineScope()
    val bridge = remember(plugin.id) { AgoraWebViewBridge(plugin, sandbox, scope) }

    // Read HTML once; reload if the on-disk file changes (e.g. plugin reinstall) by depending
    // on its absolute path + last-modified.
    val html = remember(uiFile?.absolutePath, uiFile?.lastModified()) {
        if (uiFile != null && uiFile.exists()) {
            bridge.bootstrapScript + "\n" + uiFile.readText()
        } else {
            bridge.bootstrapScript + "<h1>UI file missing</h1>"
        }
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
                    // Lock down host access — the page only talks to the agora bridge.
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
                    addJavascriptInterface(bridge, AgoraWebViewBridge.bridgeObjectName)
                    bridge.webView = this
                    loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
                }
            },
        )
    }

    // Release the WebView + bridge scope on exit.
    DisposableEffect(plugin.id) {
        onDispose { bridge.close() }
    }
}
