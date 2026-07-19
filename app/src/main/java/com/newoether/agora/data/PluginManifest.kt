package com.newoether.agora.data

import kotlinx.serialization.Serializable

/**
 * Manifest of a user-installed JS plugin (v1: tools-only).
 *
 * A plugin is a folder `filesDir/plugins/<[id]>/` containing this manifest plus a `main.js`.
 * The JS file exports one function per declared tool via CommonJS:
 *
 * ```js
 * exports.get_weather = function(params) {
 *   var r = fetch("https://wttr.in/" + params.city);
 *   return JSON.parse(r.body);
 * };
 * ```
 *
 * Security: [allowedHosts] is the only network egress the plugin's `fetch()` may use; empty list
 * means no network at all. Hosts are matched case-insensitively against the URL's host, with
 * subdomain wildcarding (`api.example.com` matches `api.example.com` and `*.api.example.com`).
 */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    /** Hostnames the plugin may contact via `fetch()`. Empty = network disabled. */
    val allowedHosts: List<String> = emptyList(),
    val tools: List<PluginTool> = emptyList(),
    /**
     * Optional UI page. When set, the plugin is expected to ship an HTML file with this name
     * alongside main.js; [com.newoether.agora.plugin.PluginLoader] extracts and validates it.
     * The HTML is rendered in a sandboxed WebView whose `agora` JS bridge can invoke any tool
     * declared in [tools]. `null` = headless plugin (tools only, no UI).
     */
    val ui: String? = null,
)

@Serializable
data class PluginTool(
    val name: String,
    val description: String,
    val parameters: List<PluginToolParam> = emptyList(),
)

@Serializable
data class PluginToolParam(
    val name: String,
    val type: String = "string",           // "string" | "number" | "integer" | "boolean"
    val required: Boolean = false,
    val description: String = "",
)
