package com.orangeisland.app.tool

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.workflow.trigger.AppForegroundDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Navigation tool provider — lets the AI open URLs, launch other apps, open system settings,
 * and share text from Orange Island to external apps. Also exposes the list of installed apps
 * so the model can pick valid package names.
 */
class NavigationToolProvider(private val app: Application) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.navigationEnabled) return emptyList()

        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "open_url",
                description = "Open a web URL in the user's default browser. " +
                    "Useful for looking up information, opening documentation, or navigating " +
                    "to a specific webpage. Returns success or an error message.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "url" to ToolProperty("string", "The full URL to open (e.g. https://example.com).")
                    ),
                    required = listOf("url")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "open_app",
                description = "Launch an installed Android app by its package name. " +
                    "Common packages: com.android.settings (Settings), com.tencent.mm (WeChat), " +
                    "com.tencent.mobileqq (QQ), com.netease.cloudmusic (NetEase Music), " +
                    "com.taobao.taobao (Taobao), com.android.chrome (Chrome), " +
                    "com.android.dialer (Phone). Call get_installed_apps first if unsure of the " +
                    "exact package name. Returns success or an error.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "package_name" to ToolProperty("string", "The Android package name (e.g. com.tencent.mm for WeChat)."),
                        "extra_text" to ToolProperty("string", "Optional text to pass to the app as Intent.EXTRA_TEXT (e.g. for sharing into the app).")
                    ),
                    required = listOf("package_name")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "open_settings",
                description = "Open a specific Android system settings screen. " +
                    "Common actions: android.settings.SETTINGS (main settings), " +
                    "android.settings.WIFI_SETTINGS, android.settings.LOCATION_SOURCE_SETTINGS, " +
                    "android.settings.DISPLAY_SETTINGS, android.settings.APPLICATION_DETAILS_SETTINGS. " +
                    "Returns success or error.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "action" to ToolProperty("string", "The settings intent action (e.g. android.settings.WIFI_SETTINGS).")
                    ),
                    required = listOf("action")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "share_text",
                description = "Share text content to another app via the Android share sheet. " +
                    "The user will pick which app to share to. Useful for sending messages, " +
                    "code snippets, or summaries to messaging apps, email, etc. Returns success or error.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "text" to ToolProperty("string", "The text content to share."),
                        "subject" to ToolProperty("string", "Optional subject/title for the share (e.g. email subject).")
                    ),
                    required = listOf("text")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_installed_apps",
                description = "List installed apps on the device (name + package name). " +
                    "Use this before open_app to find the correct package name. " +
                    "Optionally filter by a query string matched against the app name. " +
                    "Returns a JSON array of apps.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to ToolProperty("string", "Optional filter query (matched case-insensitively against app name)."),
                        "limit" to ToolProperty("integer", "Max number of results (optional, default 30).")
                    ),
                    required = emptyList()
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "get_foreground_app_nav",
                description = "Get the currently foreground app package name and app name, " +
                    "via the accessibility-based navigation tracker. Returns the package name " +
                    "(e.g. com.tencent.mm), resolved app name, or null if unknown. If you need " +
                    "usage duration too, prefer get_foreground_app instead.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        return try {
            when (name) {
                "open_url" -> openUrl(arguments)
                "open_app" -> openApp(arguments)
                "open_settings" -> openSettings(arguments)
                "share_text" -> shareText(arguments)
                "get_installed_apps" -> getInstalledApps(arguments)
                "get_foreground_app_nav" -> getForegroundApp()
                else -> unknownTool(name)
            }
        } catch (e: Exception) {
            error("execution_error", e.message ?: "Unknown error")
        }
    }

    override fun handles(name: String): Boolean = name in setOf(
        "open_url", "open_app", "open_settings", "share_text", "get_installed_apps", "get_foreground_app_nav"
    )

    // ── Implementation ─────────────────────────────────────

    /**
     * On Android 10+ (targetSdk 29+), a backgrounded process that calls [android.content.Context.
     * startActivity] to launch another app is silently dropped by the system unless it holds the
     * SYSTEM_ALERT_WINDOW (overlay) permission. Workflows (esp. background-triggered) hit exactly
     * that path, so before any of the four launching methods actually start an Activity we check:
     * either the overlay permission is granted, or our own process is currently in the foreground.
     * Returns null if the launch may proceed, otherwise a JSON error string explaining the block.
     */
    private fun backgroundLaunchGuard(): String? {
        if (Settings.canDrawOverlays(app)) return null
        if (isOwnProcessForeground()) return null
        return error(
            "background_activity_blocked",
            "Cannot launch from the background: the overlay permission (display over other apps) " +
                "is required. Ask the user to enable it in Settings → Device Access."
        )
    }

    /**
     * Best-effort foreground check for this app's own process. On Android 5.0+
     * [ActivityManager.getRunningAppProcesses] only returns the caller's own process, so its
     * importance is a reliable "are we foregrounded?" signal without needing any extra permission.
     */
    private fun isOwnProcessForeground(): Boolean = try {
        val am = app.getSystemService(Application.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val procs = am.runningAppProcesses
        procs.any {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    } catch (_: Throwable) {
        false
    }

    private fun openUrl(arguments: String): String {
        val args = json.parseToJsonElement(arguments).jsonObject
        val url = args["url"]?.toString()?.trim('"') ?: return error("missing_argument", "url is required")
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return error("invalid_url", "Cannot parse URL: $url")

        backgroundLaunchGuard()?.let { return it }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            app.startActivity(intent)
            success("Opened URL: $url")
        } catch (e: Exception) {
            error("open_failed", "No app can handle this URL: $url")
        }
    }

    private fun openApp(arguments: String): String {
        val args = json.parseToJsonElement(arguments).jsonObject
        val packageName = args["package_name"]?.toString()?.trim('"') ?: return error("missing_argument", "package_name is required")

        // Use getLaunchIntentForPackage() — this respects Android's package-visibility model
        // (the <queries> block in the manifest exposes all launcher-icon apps). No need for
        // the restricted QUERY_ALL_PACKAGES permission.
        val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName)
            ?: return error("not_found", "App not found (or not visible): $packageName. Try get_installed_apps to see what's available.")

        val argsMap = args
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        argsMap["extra_text"]?.toString()?.trim('"')?.let { text ->
            launchIntent.putExtra(Intent.EXTRA_TEXT, text)
        }

        backgroundLaunchGuard()?.let { return it }
        return try {
            app.startActivity(launchIntent)
            success("Launched app: $packageName")
        } catch (e: Exception) {
            error("launch_failed", "Failed to launch $packageName: ${e.message}")
        }
    }

    private fun openSettings(arguments: String): String {
        val args = json.parseToJsonElement(arguments).jsonObject
        val action = args["action"]?.toString()?.trim('"') ?: return error("missing_argument", "action is required")

        backgroundLaunchGuard()?.let { return it }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            app.startActivity(intent)
            success("Opened settings: $action")
        } catch (e: Exception) {
            error("open_failed", "Failed to open settings: ${e.message}")
        }
    }

    private fun shareText(arguments: String): String {
        val args = json.parseToJsonElement(arguments).jsonObject
        val text = args["text"]?.toString()?.trim('"') ?: return error("missing_argument", "text is required")
        val subject = args["subject"]?.toString()?.trim('"')

        backgroundLaunchGuard()?.let { return it }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            app.startActivity(chooser)
            success("Share sheet opened")
        } catch (e: Exception) {
            error("share_failed", "Failed to open share sheet: ${e.message}")
        }
    }

    private fun getInstalledApps(arguments: String): String {
        val args = json.parseToJsonElement(arguments).jsonObject
        val query = args["query"]?.toString()?.trim('"')?.lowercase()
        val limit = args["limit"]?.toString()?.trim('"')?.toIntOrNull() ?: 30

        // Use queryIntentActivities with the standard LAUNCHER intent — this respects
        // Android's package-visibility model and returns only user-visible apps (the ones
        // with a launcher icon). Backed by the <queries> block in AndroidManifest.xml.
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = app.packageManager
        val apps = pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                if (query == null || label.lowercase().contains(query)) {
                    buildJsonObject {
                        put("name", JsonPrimitive(label))
                        put("package_name", JsonPrimitive(pkg))
                    }
                } else null
            }
            .sortedBy { it["name"].toString().trim('"').lowercase() }
            .take(limit)

        return buildJsonObject {
            put("success", JsonPrimitive(true))
            put("count", JsonPrimitive(apps.size))
            put("apps", JsonArray(apps))
        }.toString()
    }

    private fun getForegroundApp(): String {
        val pkg = AppForegroundDispatcher.lastKnown
            ?: return error("unknown", "No foreground app detected yet")
        val appName = try {
            app.packageManager.getApplicationLabel(
                app.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }
        return buildJsonObject {
            put("success", JsonPrimitive(true))
            put("package_name", JsonPrimitive(pkg))
            put("app_name", JsonPrimitive(appName))
        }.toString()
    }

    // ── Helpers ────────────────────────────────────────────

    private fun success(message: String): String =
        buildJsonObject { put("success", JsonPrimitive(true)); put("message", JsonPrimitive(message)) }.toString()

    private fun error(type: String, message: String): String =
        buildJsonObject { put("success", JsonPrimitive(false)); put("error_type", JsonPrimitive(type)); put("message", JsonPrimitive(message)) }.toString()

    private fun unknownTool(name: String): String =
        error("unknown_tool", "NavigationToolProvider does not handle tool: $name")
}
