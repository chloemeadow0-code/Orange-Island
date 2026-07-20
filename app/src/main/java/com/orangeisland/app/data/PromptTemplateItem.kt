package com.orangeisland.app.data

import com.orangeisland.app.util.Constants
import kotlinx.serialization.Serializable
import java.util.UUID

enum class PromptItemType { CUSTOM, PREDEFINED }

@Serializable
data class PromptTemplateItem(
    val id: String = UUID.randomUUID().toString(),
    val type: PromptItemType,
    val value: String
)

object PredefinedVariables {
    const val TIME = "time"
    const val DATE = "date"
    const val SENT_TIME = "sent_time"
    const val SENT_DATE = "sent_date"
    const val ACTIVE_MEMORY = "active_memory"
    const val MODEL_ID = "model_id"

    // Ordered for the variable picker
    val ALL = listOf(TIME, DATE, SENT_TIME, SENT_DATE, ACTIVE_MEMORY, MODEL_ID)

    // Placeholders resolved per-message by applyUserTemplate
    val PER_MESSAGE_VARS = setOf(SENT_TIME, SENT_DATE)

    val EXAMPLE_VALUES = mapOf(
        TIME to "14:30:00",
        DATE to "2026-05-10",
        SENT_TIME to "10:05:00",
        SENT_DATE to "2026-05-11",
        ACTIVE_MEMORY to "[Example memory content]",
        MODEL_ID to Constants.EXAMPLE_MODEL_ID
    )

    fun compile(
        items: List<PromptTemplateItem>,
        runtimeValues: Map<String, String>,
        exampleValues: Map<String, String> = EXAMPLE_VALUES
    ): String {
        return items.joinToString("") { item ->
            when (item.type) {
                PromptItemType.CUSTOM -> item.value
                PromptItemType.PREDEFINED -> runtimeValues[item.value]
                    ?: exampleValues[item.value]
                    ?: "{${item.value}}"
            }
        }
    }
}
