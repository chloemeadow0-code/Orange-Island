package com.orangeisland.app.tool

import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * User-interaction tool provider — lets the AI ask the user a multiple-choice question via
 * a card-style dialog. The tool call suspends until the user picks one (or more) options
 * and taps confirm, or cancels the dialog.
 *
 * This bridges the LLM tool-calling path into the UI layer through [UserInteractionGate].
 */
class UserInteractionToolProvider(
    private val gate: UserInteractionGate?
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunction(
            name = "ask_user_choice",
            description = "向用户展示一个卡片式选项列表并等待选择。用于需要用户主观判断、" +
                "偏好确认、多选一/多选多的场景。工具会挂起直到用户确认或取消。" +
                "单选模式返回一个 id，多选模式返回 id 数组。" +
                "当 allow_custom=true 时，卡片底部会多出一个文本输入框，用户可以选择预设选项，" +
                "也可以输入自定义内容。",
            parameters = ToolParameters(
                properties = mapOf(
                    "question" to ToolProperty(
                        "string",
                        "询问的问题标题，简短清晰（如：你想使用哪种风格？）"
                    ),
                    "options" to ToolProperty(
                        "array",
                        "选项列表，每个元素是一个对象，必须包含字符串字段 id 和 label。" +
                            "示例：[{\"id\":\"casual\",\"label\":\"casual\"},{\"id\":\"formal\",\"label\":\"formal\"}]"
                    ),
                    "mode" to ToolProperty(
                        "string",
                        "选择模式：single（单选，用户只能选一项）或 multiple（多选，用户可选多项）"
                    ),
                    "allow_custom" to ToolProperty(
                        "boolean",
                        "是否允许用户输入自定义内容。为 true 时卡片底部显示文本输入框，" +
                            "用户可输入预设选项以外的内容。默认为 false。"
                    )
                ),
                required = listOf("question", "options", "mode")
            )
        ))
    )

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        return try {
            when (name) {
                "ask_user_choice" -> askUserChoice(arguments)
                else -> unknownTool(name)
            }
        } catch (e: Exception) {
            error("execution_error", e.message ?: "Unknown error")
        }
    }

    override fun handles(name: String): Boolean = name == "ask_user_choice"

    // ── Implementation ─────────────────────────────────────

    private suspend fun askUserChoice(arguments: String): String {
        if (gate == null) {
            return error("gate_unavailable", "User interaction gate is not installed (background context).")
        }

        val args = json.parseToJsonElement(arguments).jsonObject
        val question = args["question"]?.jsonPrimitive?.content?.trim()
            ?: return error("missing_argument", "question is required")
        val mode = args["mode"]?.jsonPrimitive?.content?.trim()?.lowercase()
            ?: return error("missing_argument", "mode is required")
        if (mode !in setOf("single", "multiple")) {
            return error("invalid_mode", "mode must be 'single' or 'multiple'")
        }

        val optionsArray = args["options"] as? JsonArray
            ?: return error("missing_argument", "options must be a JSON array")
        if (optionsArray.isEmpty()) {
            return error("invalid_options", "options array must not be empty")
        }

        val options = optionsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content?.trim()
            val label = obj["label"]?.jsonPrimitive?.content?.trim()
            if (id.isNullOrEmpty() || label.isNullOrEmpty()) return@mapNotNull null
            UserInteractionGate.ChoiceOption(id = id, label = label)
        }
        if (options.isEmpty()) {
            return error("invalid_options", "options array contains no valid items with id and label")
        }

        val allowCustom = args["allow_custom"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        // Suspend until the user interacts with the dialog.
        return gate.request(question, options, mode, allowCustom)
    }

    // ── Helpers ────────────────────────────────────────────

    private fun error(type: String, message: String): String =
        buildJsonObject {
            put("success", JsonPrimitive(false))
            put("error_type", JsonPrimitive(type))
            put("message", JsonPrimitive(message))
        }.toString()

    private fun unknownTool(name: String): String =
        error("unknown_tool", "UserInteractionToolProvider does not handle tool: $name")
}
