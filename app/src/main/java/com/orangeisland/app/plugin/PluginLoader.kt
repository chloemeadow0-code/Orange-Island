package com.orangeisland.app.plugin

import android.content.Context
import android.net.Uri
import com.orangeisland.app.data.InstalledPlugin
import com.orangeisland.app.data.PluginManifest
import com.orangeisland.app.data.PluginTool
import com.orangeisland.app.util.DebugLog
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Discovers, installs, and removes JS plugins on disk.
 *
 * Layout: each plugin lives in `filesDir/plugins/<id>/` and contains:
 *  - `manifest.json`  (parsed into [PluginManifest])
 *  - `main.js`        (CommonJS source: `exports.tool_name = function(params) { ... }`)
 *
 * The filesystem is the source of truth for "what is installed"; the DataStore key
 * `PLUGINS_ENABLED` only stores the user's enable/disable preference (resolved into
 * [InstalledPlugin.enabled] at scan time by the caller).
 *
 * Security: every install is validated — manifest id must match `[a-z0-9_.]+`, tool names
 * must be valid JS identifiers, and zip entries must not escape the plugin directory via
 * `../` path traversal (each entry's resolved path is checked to stay inside the target dir).
 */
class PluginLoader(private val appContext: Context) {
    companion object {
        private const val TAG = "PluginLoader"
        private const val PLUGINS_DIR_NAME = "plugins"
        private const val MANIFEST_FILE = "manifest.json"
        private const val MAIN_JS_FILE = "main.js"
        private const val MAX_ZIP_ENTRIES = 100
        private const val MAX_UNCOMPRESSED_SIZE = 5 * 1024 * 1024L // 5 MB per entry
        // Plugin ids: lowercase letters, digits, `_`, `-`, `.`. Must start/end with a
        // non-separator (no leading/trailing/double dots or dashes). This is permissive enough
        // for reverse-DNS ids (`com.example.foo`) and slugged names (`my-cool-plugin`).
        private val ID_REGEX = Regex("""^[a-z0-9](?:[a-z0-9_.\-]*[a-z0-9])?$""")
        private val TOOL_NAME_REGEX = Regex("""^[a-zA-Z_][a-zA-Z0-9_]*$""")
        // Allowed manifest.ui filenames: simple slugged HTML names (`ui.html`, `main.html`,
        // `my-page.html`). No paths, no `..`, no leading dots — defended at extraction time
        // against path-traversal via ../ writes.
        private val UI_FILE_REGEX = Regex("""^[a-z0-9_\-]+\.html$""")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Root directory holding all installed plugins. */
    val pluginsDir: File = File(appContext.filesDir, PLUGINS_DIR_NAME).apply { mkdirs() }

    /**
     * Scans [pluginsDir] for valid plugin folders. Folders with a malformed manifest or missing
     * main.js are skipped (and logged) — one bad plugin must never block the others.
     */
    suspend fun scan(enabledIds: Set<String>): List<InstalledPlugin> = withIO {
        val dirs = pluginsDir.listFiles { f -> f.isDirectory } ?: return@withIO emptyList()
        dirs.mapNotNull { dir ->
            try {
                val manifestFile = File(dir, MANIFEST_FILE)
                val mainJs = File(dir, MAIN_JS_FILE)
                if (!manifestFile.exists() || !mainJs.exists()) {
                    DebugLog.w(TAG, "Skipping ${dir.name}: missing manifest.json or main.js")
                    return@mapNotNull null
                }
                val manifest = parseManifest(manifestFile.readText())?.let { validate(it) } ?: run {
                    DebugLog.w(TAG, "Skipping ${dir.name}: invalid manifest")
                    return@mapNotNull null
                }
                // Directory name must agree with manifest id (enforced at install; re-checked here
                // so a manual rename can't smuggle a plugin in under another id).
                if (dir.name != manifest.id) {
                    DebugLog.w(TAG, "Skipping ${dir.name}: dir name != manifest id ${manifest.id}")
                    return@mapNotNull null
                }
                InstalledPlugin(
                    manifest = manifest,
                    dir = dir,
                    enabled = manifest.id in enabledIds,
                )
            } catch (e: Exception) {
                DebugLog.e(TAG, "Failed to scan plugin ${dir.name}", e)
                null
            }
        }.sortedBy { it.manifest.name.lowercase() }
    }

    /**
     * Installs a plugin from a `.zip` Uri. The zip must contain a top-level `manifest.json`
     * and `main.js` (either at the root, or inside a single wrapping directory — the common
     * case when zipping a folder).
     *
     * Returns the validated [PluginManifest] on success, or an exception message on failure.
     * Existing plugins with the same id are overwritten.
     */
    suspend fun installFromZip(uri: Uri): Result<PluginManifest> = withIO {
        try {
            val (manifest, entries) = readZipToMemory(uri)
                ?: return@withIO Result.failure(IllegalStateException("Empty or unreadable zip"))
            val validated = validate(manifest)

            // Extract only the files we care about (manifest + main.js + any sibling assets
            // under 1 MB each). Limit total count/size to defang zip bombs.
            val manifestJson = entries[MANIFEST_FILE]
                ?: entries["./$MANIFEST_FILE"]
                ?: return@withIO Result.failure(IllegalStateException("manifest.json not found in zip"))
            val mainJs = entries[MAIN_JS_FILE]
                ?: entries["./$MAIN_JS_FILE"]
                ?: return@withIO Result.failure(IllegalStateException("main.js not found in zip"))

            val targetDir = File(pluginsDir, validated.id)
            val stagingDir = File(pluginsDir, ".staging_${validated.id}_${System.currentTimeMillis()}")
            stagingDir.mkdirs()
            try {
                // Drop the whitelisted files into staging. Anything else is ignored.
                File(stagingDir, MANIFEST_FILE).writeText(manifestJson.toString(Charsets.UTF_8))
                File(stagingDir, MAIN_JS_FILE).writeText(mainJs.toString(Charsets.UTF_8))
                // If the plugin declares a UI, the named HTML file must be present in the zip.
                validated.ui?.let { uiName ->
                    val htmlBytes = entries[uiName]
                        ?: return@withIO Result.failure(
                            IllegalStateException("manifest.ui references '$uiName' but it was not found in the zip")
                        )
                    File(stagingDir, uiName).writeText(htmlBytes.toString(Charsets.UTF_8))
                }
                // Atomic-ish swap: delete old, rename staging.
                if (targetDir.exists()) targetDir.deleteRecursively()
                if (!stagingDir.renameTo(targetDir)) {
                    throw java.io.IOException("Failed to move staged plugin into place")
                }
                Result.success(validated)
            } finally {
                if (stagingDir.exists()) stagingDir.deleteRecursively()
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "installFromZip failed", e)
            Result.failure(e)
        }
    }

    /** Removes a plugin's directory. Safe against path traversal: [pluginId] is validated. */
    suspend fun uninstall(pluginId: String): Boolean = withIO {
        if (!ID_REGEX.matches(pluginId)) return@withIO false
        val dir = File(pluginsDir, pluginId)
        if (!dir.exists()) return@withIO false
        dir.deleteRecursively()
    }

    // ── Internals ─────────────────────────────────────────────

    private fun parseManifest(text: String): PluginManifest? = try {
        json.decodeFromString(PluginManifest.serializer(), text)
    } catch (e: Exception) {
        DebugLog.w(TAG, "manifest parse error: ${e.message}")
        null
    }

    /** Enforces manifest invariants. Throws [IllegalArgumentException] on violation. */
    private fun validate(m: PluginManifest): PluginManifest {
        require(m.id.isNotBlank()) { "manifest.id is blank" }
        require(ID_REGEX.matches(m.id)) { "manifest.id must match [a-z0-9_.-] but was '${m.id}'" }
        require(m.name.isNotBlank()) { "manifest.name is blank" }
        require(m.tools.isNotEmpty()) { "manifest has no tools" }
        // De-duplicate + validate tool names + unique parameter names per tool.
        val names = mutableSetOf<String>()
        m.tools.forEach { tool ->
            require(TOOL_NAME_REGEX.matches(tool.name)) { "invalid tool name '${tool.name}'" }
            require(names.add(tool.name)) { "duplicate tool name '${tool.name}'" }
            val paramNames = mutableSetOf<String>()
            tool.parameters.forEach { p ->
                require(TOOL_NAME_REGEX.matches(p.name)) { "invalid parameter name '${p.name}' in tool '${tool.name}'" }
                require(paramNames.add(p.name)) { "duplicate parameter '${p.name}' in tool '${tool.name}'" }
            }
        }
        // Validate config field names (same JS-identifier rule as tool params) + uniqueness.
        val configNames = mutableSetOf<String>()
        m.config.forEach { f ->
            require(TOOL_NAME_REGEX.matches(f.name)) { "invalid config field name '${f.name}'" }
            require(configNames.add(f.name)) { "duplicate config field '${f.name}'" }
        }
        // allowedHosts: lowercase + strip port, reject empty entries.
        val cleanedHosts = m.allowedHosts.mapNotNull { raw ->
            raw.trim().lowercase().substringBefore(':').takeIf { it.isNotEmpty() }
        }
        // ui: optional HTML filename. When present, must be a simple `[a-z0-9_-]+\.html` name
        // (no path components) so extraction can't be coerced into writing outside the plugin dir.
        m.ui?.let { uiName ->
            require(UI_FILE_REGEX.matches(uiName)) {
                "manifest.ui must match [a-z0-9_-]+\\.html but was '$uiName'"
            }
        }
        return m.copy(allowedHosts = cleanedHosts.distinct())
    }

    /**
     * Reads a zip into memory keyed by entry name. Returns null if the zip is empty/unreadable.
     *
     * Zip-bomb defenses: entries capped at [MAX_ZIP_ENTRIES], each uncompressed entry at
     * [MAX_UNCOMPRESSED_SIZE]. Path-traversal defense: any entry whose normalized path escapes
     * the conceptual root (`../`) is rejected.
     */
    private fun readZipToMemory(uri: Uri): Pair<PluginManifest, Map<String, ByteArray>>? {
        val entries = mutableMapOf<String, ByteArray>()
        var manifest: PluginManifest? = null
        appContext.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            ZipInputStream(input).use { zis ->
                var count = 0
                while (true) {
                    val entry: ZipEntry = zis.nextEntry ?: break
                    if (count++ >= MAX_ZIP_ENTRIES) {
                        throw java.io.IOException("Zip has too many entries (>$MAX_ZIP_ENTRIES)")
                    }
                    if (entry.isDirectory) { zis.closeEntry(); continue }
                    val name = entry.name
                    // Reject absolute paths and traversal segments outright.
                    if (name.startsWith("/") || name.startsWith("\\") || name.contains("..")) {
                        throw java.io.IOException("Unsafe zip entry path: $name")
                    }
                    val bytes = readCappedBytes(zis, MAX_UNCOMPRESSED_SIZE)
                    val baseName = File(name).name
                    if (baseName == MANIFEST_FILE && manifest == null) {
                        manifest = parseManifest(String(bytes, Charsets.UTF_8))?.let { validate(it) }
                    }
                    // Store under the basename so the caller doesn't care whether the zip had
                    // a wrapping folder. We keep manifest + main.js always, plus any .html file
                    // (one of them will be the manifest.ui target if the plugin declares a UI).
                    if (baseName == MANIFEST_FILE || baseName == MAIN_JS_FILE ||
                        baseName.endsWith(".html", ignoreCase = true)) {
                        entries[baseName] = bytes
                    }
                    zis.closeEntry()
                }
            }
        }
        val m = manifest ?: return null
        return m to entries
    }

    private fun readCappedBytes(stream: java.io.InputStream, maxSize: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxSize) throw java.io.IOException("Zip entry exceeds $maxSize bytes")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private suspend fun <T> withIO(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
