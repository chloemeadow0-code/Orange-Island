package com.orangeisland.app.api

/**
 * Resolves the OpenAI-compatible "should the Base URL include /v1?" ambiguity.
 *
 * Design: the ambiguity is resolved **once, at configuration time**
 * (see [com.orangeisland.app.viewmodel.ProviderRegistry.fetchModelsForProvider]),
 * and the canonical Base URL is persisted. The request hot path then uses a single
 * deterministic endpoint instead of trying both forms (and eating a 404) on every call.
 */
object BaseUrlResolver {
    /**
     * Matches an API version segment anywhere in the path: `/v1`, `/v1beta`,
     * `/compatible-mode/v1`, `/v2`, … If present, the URL is already canonical and
     * must not have `/v1` appended.
     */
    private val VERSION_SEGMENT = Regex("""/v\d""")

    fun hasVersionSegment(url: String): Boolean =
        VERSION_SEGMENT.containsMatchIn(url.trimEnd('/'))

    /** Appends `/v1` unless the URL is blank or already carries a version segment. */
    fun withV1(url: String): String {
        val trimmed = url.trimEnd('/')
        return if (trimmed.isBlank() || hasVersionSegment(trimmed)) trimmed else "$trimmed/v1"
    }
}
