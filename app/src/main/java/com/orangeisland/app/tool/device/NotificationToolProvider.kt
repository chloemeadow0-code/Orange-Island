package com.orangeisland.app.tool.device

import android.app.Application
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.tool.SensitiveToolApprovalGate
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.PermissionController
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notification tool. Reads from [DeviceNotificationListenerService]'s in-memory ring buffer,
 * which is populated by the system only while the user has enabled our listener under
 * Settings → Notification access.
 *
 * One tool:
 *  - [list_notifications] — the current active notifications (newest first), with title, text,
 *    app label, post time, and ongoing flag. Optional package filter + limit.
 *
 * The provider does NOT request permission at execute time — the notification-listener
 * permission can only be granted from system Settings, and the settings page surfaces the
 * 'Open system settings' affordance via PermissionController. Here we just report the state.
 */
class NotificationToolProvider(
    private val app: Application,
    private val permissionController: PermissionController,
    private val approvalGate: SensitiveToolApprovalGate? = null,
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.notificationEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "list_notifications",
                description = "List the device's currently active notifications (newest first). " +
                    "Each entry has the app name, title, body text, and post time. Use when the " +
                    "user asks 'what notifications do I have', '我有什么通知', or wants a summary of " +
                    "missed alerts. Optionally filter by package or limit the count.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "limit" to ToolProperty("integer", "Max notifications to return (default 20, max 100)."),
                        "package" to ToolProperty("string", "Optional package-name filter, e.g. 'com.tencent.mm'.")
                    ),
                    required = emptyList()
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (name != "list_notifications") return unknownTool(name)
        if (!permissionController.isGranted(PermissionController.Tool.NOTIFICATION)) {
            return error("permission_denied",
                "Notification access not granted. Ask the user to enable Notifications in Settings → Device Access (it opens the system Notification access screen).")
        }
        if (!DeviceNotificationListenerService.companionActive) {
            // Permission granted flag flipped on but service not yet bound — happens right after
            // the user toggles access. The next call will work once the system binds the listener.
            return error("not_yet_active",
                "Listener permission is granted but the service hasn't bound yet. Try again in a moment.")
        }
        if (approvalGate?.approval?.invoke(name, "读取最近通知内容") != true) {
            return error("approval_denied", "用户拒绝了通知读取请求。")
        }
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val limit = ((parsed["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val pkgFilter = (parsed["package"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val pm = app.packageManager
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val now = System.currentTimeMillis()
        val notifications = DeviceNotificationListenerService.snapshot()
            .asSequence()
            .filter { pkgFilter == null || it.packageName == pkgFilter }
            .take(limit)
            .map { n ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString()
                }.getOrDefault(n.packageName)
                buildJsonObject {
                    put("app", label)
                    put("package", n.packageName)
                    if (n.title.isNotBlank()) put("title", n.title)
                    if (n.text.isNotBlank()) put("text", n.text)
                    put("posted_at", iso.format(Date(n.postTime)))
                    put("age_seconds", ((now - n.postTime) / 1000).coerceAtLeast(0))
                    put("ongoing", n.isOngoing)
                }
            }
            .toList()
        return buildJsonObject {
            put("notifications", buildJsonArray { notifications.forEach { add(it) } })
            put("count", notifications.size)
        }.toString()
    }

    override fun handles(name: String): Boolean = name == "list_notifications"

    private fun error(type: String, message: String): String =
        buildJsonObject { put("error", type); put("message", message) }.toString()

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
}
