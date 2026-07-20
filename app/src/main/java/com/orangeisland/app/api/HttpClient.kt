package com.orangeisland.app.api

import okhttp3.MediaType.Companion.toMediaType
import com.orangeisland.app.util.DebugLog
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpClient {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Header names that carry secret credentials across the providers.
    private val CREDENTIAL_HEADERS = setOf("authorization", "x-api-key", "x-goog-api-key", "api-key")

    /** True for loopback / RFC-1918 / link-local hosts and bare LAN hostnames
     *  (e.g. "ollama", "nas.local"). Public FQDNs like api.openai.com return false. */
    private fun isLocalHost(host: String): Boolean {
        if (host.isBlank()) return false
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h == "::1" || h.endsWith(".local") || h.endsWith(".lan") ||
            h.endsWith(".home") || h.endsWith(".internal")) return true
        // Bare hostname with no dot → LAN name, not a public domain.
        if (!h.contains('.')) return true
        val o = h.split('.')
        if (o.size == 4 && o.all { it.toIntOrNull() in 0..255 }) {
            val a = o[0].toInt(); val b = o[1].toInt()
            return a == 127 || a == 10 || (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) || (a == 169 && b == 254)
        }
        return false
    }

    /** Fail-closed guard: never transmit API credentials over cleartext HTTP to a
     *  non-local host. LAN/loopback endpoints (Ollama, self-hosted) stay allowed. */
    private fun guardCleartextCredentials(url: String, headers: Map<String, String>) {
        if (!url.startsWith("http://", ignoreCase = true)) return
        val host = try { java.net.URI(url).host ?: "" } catch (_: Exception) { "" }
        if (isLocalHost(host)) return
        if (headers.keys.any { it.lowercase() in CREDENTIAL_HEADERS }) {
            throw IOException("Refusing to send API credentials over cleartext HTTP to \"$host\". Use an https:// endpoint.")
        }
    }

    // ── Network proxy ─────────────────────────────────────────────────────
    enum class ProxyType { HTTP, SOCKS }

    /** Active proxy config, or null = direct connection. Read live by the proxy
     *  selector, so changing it takes effect immediately without rebuilding the client. */
    data class ProxyConfig(
        val type: ProxyType,
        val host: String,
        val port: Int,
        val username: String = "",
        val password: String = "",
        /** Hosts/CIDRs that bypass the proxy (e.g. localhost, 192.168.0.0/16). */
        val bypass: List<String> = emptyList()
    )

    @Volatile private var proxyConfig: ProxyConfig? = null

    /** Apply (or clear) the proxy. Also installs a default [java.net.Authenticator] for
     *  SOCKS proxy auth, which OkHttp's proxyAuthenticator does not cover. */
    fun setProxy(config: ProxyConfig?) {
        proxyConfig = config?.takeIf { it.host.isNotBlank() && it.port in 1..65535 }
        val cfg = proxyConfig
        if (cfg != null && cfg.username.isNotBlank()) {
            java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                override fun getPasswordAuthentication(): java.net.PasswordAuthentication? =
                    if (requestorType == RequestorType.PROXY)
                        java.net.PasswordAuthentication(cfg.username, cfg.password.toCharArray())
                    else null
            })
        } else {
            java.net.Authenticator.setDefault(null)
        }
    }

    private fun resolveProxy(host: String): java.net.Proxy {
        val cfg = proxyConfig ?: return java.net.Proxy.NO_PROXY
        if (isProxyBypassed(host, cfg.bypass)) return java.net.Proxy.NO_PROXY
        val type = if (cfg.type == ProxyType.SOCKS) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
        return java.net.Proxy(type, java.net.InetSocketAddress.createUnresolved(cfg.host, cfg.port))
    }

    /** True if [host] matches a bypass entry: exact host, `*.suffix` wildcard, or IPv4 CIDR. */
    private fun isProxyBypassed(host: String, bypass: List<String>): Boolean {
        if (host.isBlank()) return true
        val h = host.lowercase().trim('[', ']')
        for (raw in bypass) {
            val entry = raw.trim().lowercase()
            when {
                entry.isEmpty() -> continue
                entry.contains('/') -> if (ipv4InCidr(h, entry)) return true
                entry.startsWith("*.") -> if (h == entry.drop(2) || h.endsWith(entry.drop(1))) return true
                else -> if (h == entry) return true
            }
        }
        return false
    }

    private fun ipv4ToLong(ip: String): Long? {
        val o = ip.split('.')
        if (o.size != 4) return null
        var v = 0L
        for (p in o) { val n = p.toIntOrNull() ?: return null; if (n !in 0..255) return null; v = (v shl 8) or n.toLong() }
        return v
    }

    private fun ipv4InCidr(host: String, cidr: String): Boolean {
        val parts = cidr.split('/')
        if (parts.size != 2) return false
        val bits = parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return false
        val ipL = ipv4ToLong(host) ?: return false
        val netL = ipv4ToLong(parts[0]) ?: return false
        val mask = if (bits == 0) 0L else (-1L shl (32 - bits)) and 0xFFFFFFFFL
        return (ipL and mask) == (netL and mask)
    }

    private val proxySelector = object : java.net.ProxySelector() {
        override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> =
            mutableListOf(resolveProxy(uri?.host ?: ""))
        override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, e: IOException?) {}
    }

    private val proxyAuthenticator = object : okhttp3.Authenticator {
        override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): Request? {
            val cfg = proxyConfig
            if (cfg == null || cfg.username.isBlank() || cfg.type != ProxyType.HTTP) return null
            if (response.request.header("Proxy-Authorization") != null) return null // already tried
            return response.request.newBuilder()
                .header("Proxy-Authorization", okhttp3.Credentials.basic(cfg.username, cfg.password))
                .build()
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .proxySelector(proxySelector)
        .proxyAuthenticator(proxyAuthenticator)
        .build()

    /** The currently active streaming handle, if any. Used to cancel
     *  generation immediately by closing the underlying socket. */
    @Volatile var activeStreamHandle: StreamHandle? = null

    class StreamHandle(private val call: okhttp3.Call, private val response: okhttp3.Response) {
        val code: Int get() = response.code
        val source: BufferedSource? get() = response.body?.source()
        val errorBody: String? by lazy {
            try { response.body?.string() } catch (_: Exception) { null }
        }
        fun close() {
            if (HttpClient.activeStreamHandle === this) {
                HttpClient.activeStreamHandle = null
            }
            response.close()
        }
        fun readLine(): String? = source?.readUtf8Line()
        /** Cancel the underlying HTTP call immediately — unblocks [readLine]. */
        fun cancel() = call.cancel()
    }

    fun streamPost(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): StreamHandle {
        guardCleartextCredentials(url, headers)
        val body = jsonBody.toRequestBody(JSON)
        val requestBuilder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val call = client.newCall(requestBuilder.build())
        val handle = StreamHandle(call, call.execute())
        activeStreamHandle = handle
        return handle
    }

    fun post(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String? {
        guardCleartextCredentials(url, headers)
        val body = jsonBody.toRequestBody(JSON)
        val requestBuilder = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        return response.use {
            if (it.isSuccessful) it.body?.string()
            else {
                DebugLog.e("HttpClient", "POST $url failed: ${it.code} ${it.body?.string()}")
                null
            }
        }
    }

    fun fetchModels(url: String, headers: Map<String, String> = emptyMap()): String? {
        guardCleartextCredentials(url, headers)
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        return response.use {
            if (it.isSuccessful) it.body?.string() else null
        }
    }

    /** Plain GET returning the response body as a string, or null on non-2xx / failure.
     *  Used by device-access tools (e.g. Amap reverse geocoding) for simple REST calls. */
    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        guardCleartextCredentials(url, headers)
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        return response.use {
            if (it.isSuccessful) it.body?.string()
            else {
                DebugLog.e("HttpClient", "GET $url failed: ${it.code} ${it.body?.string()}")
                null
            }
        }
    }

    /** GET raw bytes (e.g. an image referenced by URL). Returns null on failure. */
    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        guardCleartextCredentials(url, headers)
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        return response.use {
            if (it.isSuccessful) it.body?.bytes() else null
        }
    }
}
