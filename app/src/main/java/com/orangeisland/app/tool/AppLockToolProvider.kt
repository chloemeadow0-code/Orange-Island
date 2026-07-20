package com.orangeisland.app.tool

import android.app.Application
import android.content.Intent
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.data.AppLockEntry
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * App Lock tool provider — lets the AI lock and unlock other apps on the device.
 *
 * Locking is implemented via [AppLockAccessibilityService]: when the user opens a locked app,
 * the service shows [AppLockMaskActivity] on top, displaying the AI's message. Only the AI
 * can unlock apps — there is no PIN escape hatch.
 *
 * Package resolution supports both exact package names and fuzzy display-name matching so the
 * AI can say "lock WeChat" without knowing `com.tencent.mm`. We refuse to lock our own package.
 */
class AppLockToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val settingsManager by lazy { SettingsManager(app) }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.appLockEnabled) return emptyList()

        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "app_lock",
                description = "Lock, unlock, or list app-locking on the user's device. When an " +
                    "app is locked, the user cannot open it: a lock screen with your message " +
                    "appears on top, and ONLY you (the AI) can unlock it by calling unlock_app. " +
                    "There is no PIN escape hatch — the user must convince you to unlock. " +
                    "Actions: lock_app, unlock_app, list_locked_apps. " +
                    "This is an accessibility-based interception layer, NOT OS-level restriction " +
                    "(the user can disable the accessibility service to bypass).",
                parameters = ToolParameters(
                    properties = mapOf(
                        "action" to ToolProperty("string",
                            "One of: lock_app, unlock_app, list_locked_apps."),
                        "package_name" to ToolProperty("string",
                            "Exact Android package name (e.g. com.tencent.mm). Preferred over app_name."),
                        "app_name" to ToolProperty("string",
                            "Display name for fuzzy matching (e.g. 'WeChat' or '微信') when package_name is unknown."),
                        "message" to ToolProperty("string",
                            "Required for lock_app: a short message explaining to the user why this app is locked. " +
                                "Shown on the lock screen. Be persuasive and specific.")
                    ),
                    required = listOf("action")
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (name != "app_lock") return unknownTool(name)
        return try {
            val args = json.parseToJsonElement(arguments).jsonObject
            when (args["action"]?.toString()?.trim('"')) {
                "lock_app" -> lockApp(args)
                "unlock_app" -> unlockApp(args)
                "list_locked_apps" -> listLockedApps()
                else -> error("invalid_action", "action must be lock_app/unlock_app/list_locked_apps")
            }
        } catch (e: Exception) {
            error("execution_error", e.message ?: "Unknown error")
        }
    }

    override fun handles(name: String): Boolean = name == "app_lock"

    // ── Actions ─────────────────────────────────────────────

    private suspend fun lockApp(args: JsonObject): String {
        val message = args["message"]?.toString()?.trim('"')
            ?: return error("missing_argument", "message is required for lock_app (explain why)")

        val resolved = resolvePackage(args) ?: return error(
            "package_not_found",
            "Could not resolve a target app. Provide package_name or a matching app_name."
        )
        when (resolved) {
            is PackageResolution.Ambiguous ->
                return error("ambiguous", "Multiple apps match: ${resolved.candidates}. Specify package_name exactly.")
            is PackageResolution.NotFound ->
                return error("not_found", "No installed app matches the given name.")
            is PackageResolution.Resolved -> {
                if (resolved.packageName == app.packageName) {
                    return error("self_lock", "Refusing to lock Orange Island itself.")
                }
                val entry = AppLockEntry(
                    packageName = resolved.packageName,
                    label = resolved.label,
                    message = message,
                    createdAt = System.currentTimeMillis()
                )
                val current = settingsManager.appLockEntries.first().toMutableMap()
                current[resolved.packageName] = entry
                settingsManager.saveAppLockEntries(current)
                return success("Locked: ${resolved.label} (${resolved.packageName}). " +
                    "Only you (the AI) can unlock it via unlock_app.")
            }
        }
    }

    private suspend fun unlockApp(args: JsonObject): String {
        val resolved = resolvePackage(args) ?: return error(
            "package_not_found", "Could not resolve a target app."
        )
        val current = settingsManager.appLockEntries.first().toMutableMap()
        when (resolved) {
            is PackageResolution.Resolved -> {
                val removed = current.remove(resolved.packageName)
                if (removed == null) return error("not_locked", "${resolved.label} was not locked.")
                settingsManager.saveAppLockEntries(current)
                return success("Unlocked: ${resolved.label}")
            }
            is PackageResolution.Ambiguous ->
                return error("ambiguous", "Multiple apps match: ${resolved.candidates}")
            is PackageResolution.NotFound ->
                return error("not_found", "No installed app matches.")
        }
    }

    private suspend fun listLockedApps(): String {
        val entries = settingsManager.appLockEntries.first()
        val arr = entries.values.map { e ->
            buildJsonObject {
                put("package_name", JsonPrimitive(e.packageName))
                put("label", JsonPrimitive(e.label))
                put("message", JsonPrimitive(e.message))
            }
        }
        return buildJsonObject {
            put("success", JsonPrimitive(true))
            put("count", JsonPrimitive(arr.size))
            put("apps", JsonArray(arr))
        }.toString()
    }

    // ── Package resolution ──────────────────────────────────

    private sealed class PackageResolution {
        data class Resolved(val packageName: String, val label: String) : PackageResolution()
        data class Ambiguous(val candidates: List<String>) : PackageResolution()
        object NotFound : PackageResolution()
    }

    /** Resolve a target package from explicit package_name or fuzzy app_name. */
    private fun resolvePackage(args: JsonObject): PackageResolution? {
        val explicitPkg = args["package_name"]?.toString()?.trim('"')
        val appName = args["app_name"]?.toString()?.trim('"')
        if (explicitPkg.isNullOrBlank() && appName.isNullOrBlank()) return null

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = app.packageManager
        val allApps = pm.queryIntentActivities(launcherIntent, 0).map { ri ->
            val pkg = ri.activityInfo.packageName
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            Triple(pkg, label, label.lowercase())
        }

        // 1. Exact package name
        if (!explicitPkg.isNullOrBlank()) {
            val match = allApps.firstOrNull { it.first == explicitPkg }
            if (match != null) return PackageResolution.Resolved(match.first, match.second)
        }
        // 2. Exact display name
        if (!appName.isNullOrBlank()) {
            val lc = appName.lowercase()
            val exact = allApps.filter { it.third == lc }
            if (exact.size == 1) return PackageResolution.Resolved(exact[0].first, exact[0].second)
            if (exact.size > 1) return PackageResolution.Ambiguous(exact.map { it.first })
            // 3. Contains match
            val fuzzy = allApps.filter { it.third.contains(lc) || lc.contains(it.third) }
            if (fuzzy.size == 1) return PackageResolution.Resolved(fuzzy[0].first, fuzzy[0].second)
            if (fuzzy.size > 1) return PackageResolution.Ambiguous(fuzzy.map { "${it.second} (${it.first})" })
        }
        return PackageResolution.NotFound
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun success(message: String): String =
        buildJsonObject { put("success", JsonPrimitive(true)); put("message", JsonPrimitive(message)) }.toString()

    private fun error(type: String, message: String): String =
        buildJsonObject { put("success", JsonPrimitive(false)); put("error_type", JsonPrimitive(type)); put("message", JsonPrimitive(message)) }.toString()

    private fun unknownTool(name: String): String =
        error("unknown_tool", "AppLockToolProvider does not handle tool: $name")
}
