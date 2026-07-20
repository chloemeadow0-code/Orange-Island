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

object UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GitHubRelease(
        val tag_name: String,
        val html_url: String,
        val body: String? = null
    )

    /**
     * Check GitHub for a newer release. Returns [UpdateInfo] if an update is available,
     * or null if the current version is up-to-date or the check fails.
     */
    fun check(currentVersion: String): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/orangeisland/app/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body.string()
            response.close()

            val release = json.decodeFromString<GitHubRelease>(body)
            val latestVersion = release.tag_name.removePrefix("v")

            if (compareVersions(latestVersion, currentVersion) > 0) {
                UpdateInfo(
                    version = latestVersion,
                    url = release.html_url,
                    body = release.body.orEmpty()
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
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
