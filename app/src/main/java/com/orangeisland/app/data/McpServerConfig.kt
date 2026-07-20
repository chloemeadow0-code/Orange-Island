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
) {
    companion object {
        const val TRANSPORT_STREAMABLE = "streamable"
        const val TRANSPORT_SSE = "sse"

        fun isValidTransport(value: String): Boolean =
            value == TRANSPORT_STREAMABLE || value == TRANSPORT_SSE
    }
}
