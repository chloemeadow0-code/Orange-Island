package com.orangeisland.app.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val url: String,
    val body: String
)

/** Result of a single update check. */
sealed class UpdateCheckResult {
    /** A newer release is available. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    /** Current version is up-to-date, or no update info exists. */
    data object UpToDate : UpdateCheckResult()
    /** The check failed (network error, parsing error, server error, etc.). */
    data class Error(val reason: String) : UpdateCheckResult()
}

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 1000L

    // Public endpoint that serves a small version.json. This points to the
    // Orange-Island-Releases GitHub repo so the main (private) source repo
    // does not need to expose release assets.
    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/chloemeadow0-code/Orange-Island-Releases/main/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class VersionInfo(
        val versionName: String,
        val versionCode: Int,
        val url: String,
        val body: String? = null
    )

    private sealed class FetchResult {
        data class Success(val info: VersionInfo) : FetchResult()
        data class Error(val reason: String) : FetchResult()
    }

    /**
     * Check the public version.json endpoint for a newer release.
     *
     * @return [UpdateCheckResult.Available] if a newer release exists,
     *         [UpdateCheckResult.UpToDate] if the current version is the latest,
     *         or [UpdateCheckResult.Error] if the check could not complete.
     */
    fun check(currentVersion: String, currentVersionCode: Int = 0): UpdateCheckResult {
        DebugLog.d(TAG, "Checking $VERSION_JSON_URL against current version $currentVersion ($currentVersionCode)")

        var lastError: String? = null
        repeat(MAX_RETRIES) { attempt ->
            if (attempt > 0) {
                DebugLog.d(TAG, "Retrying update check (attempt ${attempt + 1}/$MAX_RETRIES)")
                Thread.sleep(RETRY_DELAY_MS)
            }
            when (val result = fetchVersionInfo()) {
                is FetchResult.Success -> {
                    val info = result.info
                    DebugLog.d(TAG, "Remote version: ${info.versionName} (${info.versionCode})")
                    return evaluate(info, currentVersion, currentVersionCode)
                }
                is FetchResult.Error -> {
                    lastError = result.reason
                    DebugLog.w(TAG, "Update check attempt ${attempt + 1} failed: $lastError")
                }
            }
        }

        return UpdateCheckResult.Error(lastError ?: "Network error")
    }

    private fun fetchVersionInfo(): FetchResult {
        return try {
            val request = Request.Builder()
                .url(VERSION_JSON_URL)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return FetchResult.Error("Server returned ${response.code}")
                }
                val body = response.body.string()
                FetchResult.Success(json.decodeFromString<VersionInfo>(body))
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Fetch version.json failed", e)
            FetchResult.Error(e.message ?: "Network error")
        }
    }

    private fun evaluate(
        info: VersionInfo,
        currentVersion: String,
        currentVersionCode: Int
    ): UpdateCheckResult {

        return if (compareVersions(info.versionName, currentVersion) > 0 ||
            (info.versionName == currentVersion && info.versionCode > currentVersionCode)) {
            UpdateCheckResult.Available(
                UpdateInfo(
                    version = info.versionName,
                    url = info.url,
                    body = info.body.orEmpty()
                )
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * Compare two semver strings (e.g. "1.0.10" vs "1.0.9").
     * Returns positive if [a] > [b], negative if [a] < [b], 0 if equal.
     */
    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
