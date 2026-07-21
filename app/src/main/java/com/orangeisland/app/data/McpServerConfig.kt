package com.orangeisland.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Configuration for a remote MCP (Model Context Protocol) server.
 *
 * Transport selection:
 *  - [TRANSPORT_STREAMABLE] — MCP 2025-03 spec Streamable HTTP (single endpoint, POST + GET/SSE).
 *    Use for modern remote servers (Cloudflare, Smithery, self-hosted mcp-proxy, etc.).
 *  - [TRANSPORT_SSE] — legacy 2024-11 spec double-endpoint SSE (`/sse` GET + `/messages` POST).
 *    Use for older servers that have not migrated to Streamable HTTP.
 */
@Serializable
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Full MCP endpoint URL. For streamable: the POST/GET endpoint (often `/mcp`).
     *  For SSE: the GET endpoint that yields the `endpoint` event (often `/sse`). */
    val url: String,
    val transport: String = TRANSPORT_STREAMABLE,
    /** Extra HTTP headers serialized as a JSON object string (e.g. `{"Authorization":"Bearer …"}`).
     *  Stored encrypted because headers commonly carry bearer tokens. */
    val headersJson: String = "{}",
    val enabled: Boolean = true,
    /** 被用户手动关闭的工具名（原始 MCP 工具名，不是打包后的 apiName）。
     *  这些工具不会出现在发给模型的工具定义列表里，用来控制 token 消耗。 */
    val disabledToolNames: Set<String> = emptySet(),
) {
    companion object {
        const val TRANSPORT_STREAMABLE = "streamable"
        const val TRANSPORT_SSE = "sse"

        fun isValidTransport(value: String): Boolean =
            value == TRANSPORT_STREAMABLE || value == TRANSPORT_SSE
    }
}
