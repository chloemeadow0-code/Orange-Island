package com.orangeisland.app.data

import com.orangeisland.app.model.ThinkingLevels
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.float
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull

/**
 * Extra settings serialized as JsonObject to avoid D8 field-count crash on large @Serializable classes.
 * All serialization is done at runtime (no compile-time serializer codegen).
 */
object ExportExtraSettings {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun toJsonObject(sm: SettingsManager, includeApiKeys: Boolean = true): JsonObject = buildJsonObject {
        // System prompts are exported standalone as system_prompts.json — NOT duplicated here.

        val imgTransEnabled = sm.imageTranscriptionEnabledModels.first()
        put("imageTranscriptionEnabledModels", JsonPrimitive(imgTransEnabled.joinToString(",")))
        sm.imageTranscriptionModel.first()?.let { put("imageTranscriptionModel", JsonPrimitive(it)) }
        put("imageTranscriptionBatchSize", JsonPrimitive(sm.imageTranscriptionBatchSize.first()))
        put("imageTranscriptionPrompt", JsonPrimitive(sm.imageTranscriptionPrompt.first()))

        put("webSearchNumResults", JsonPrimitive(sm.webSearchNumResults.first()))
        put("searchContextWindow", JsonPrimitive(sm.searchContextWindow.first()))
        put("searchMatchLimit", JsonPrimitive(sm.searchMatchLimit.first()))

        sm.defaultTemperature.first()?.let { put("defaultTemperature", JsonPrimitive(it)) }
        sm.defaultMaxTokens.first()?.let { put("defaultMaxTokens", JsonPrimitive(it)) }
        sm.defaultTopP.first()?.let { put("defaultTopP", JsonPrimitive(it)) }
        sm.defaultFrequencyPenalty.first()?.let { put("defaultFrequencyPenalty", JsonPrimitive(it)) }
        sm.defaultPresencePenalty.first()?.let { put("defaultPresencePenalty", JsonPrimitive(it)) }

        val conv = sm.conversationSettings.first()
        if (conv.isNotEmpty()) {
            putJsonObject("conversationSettings") {
                conv.forEach { (convId, cs) ->
                    putJsonObject(convId) {
                        cs.contextWindow?.let { put("contextWindow", JsonPrimitive(it)) }
                        cs.temperature?.let { put("temperature", JsonPrimitive(it.toDouble())) }
                        cs.maxTokens?.let { put("maxTokens", JsonPrimitive(it)) }
                        cs.topP?.let { put("topP", JsonPrimitive(it.toDouble())) }
                        cs.frequencyPenalty?.let { put("frequencyPenalty", JsonPrimitive(it.toDouble())) }
                        cs.presencePenalty?.let { put("presencePenalty", JsonPrimitive(it.toDouble())) }
                        cs.codeExecutionEnabled?.let { put("codeExecutionEnabled", JsonPrimitive(it)) }
                        cs.googleSearchEnabled?.let { put("googleSearchEnabled", JsonPrimitive(it)) }
                        cs.thinkingEnabled?.let { put("thinkingEnabled", JsonPrimitive(it)) }
                        cs.thinkingLevel?.let { put("thinkingLevel", JsonPrimitive(it)) }
                        cs.thinkingBudgetEnabled?.let { put("thinkingBudgetEnabled", JsonPrimitive(it)) }
                        cs.thinkingBudgetTokens?.let { put("thinkingBudgetTokens", JsonPrimitive(it)) }
                        cs.webSearchEnabled?.let { put("webSearchEnabled", JsonPrimitive(it)) }
                        cs.shellEnabled?.let { put("shellEnabled", JsonPrimitive(it)) }
                    }
                }
            }
        }

        put("showDocumentationFab", JsonPrimitive(sm.showDocumentationFab.first()))
        put("themeMode", JsonPrimitive(sm.themeMode.first()))
        put("colorScheme", JsonPrimitive(sm.colorScheme.first()))
        put("dynamicColor", JsonPrimitive(sm.dynamicColor.first()))
        put("blurEffectsEnabled", JsonPrimitive(sm.blurEffectsEnabled.first()))
        put("hapticsEnabled", JsonPrimitive(sm.hapticsEnabled.first()))
        put("schemeStyle", JsonPrimitive(sm.schemeStyle.first()))
        put("fontPreference", JsonPrimitive(sm.fontPreference.first()))
        put("customFontPath", JsonPrimitive(sm.customFontPath.first()))
        put("customFontName", JsonPrimitive(sm.customFontName.first()))
        put("autoUpdateCheck", JsonPrimitive(sm.autoUpdateCheck.first()))
        put("proxyEnabled", JsonPrimitive(sm.proxyEnabled.first()))
        put("proxyType", JsonPrimitive(sm.proxyType.first()))
        put("proxyHost", JsonPrimitive(sm.proxyHost.first()))
        put("proxyPort", JsonPrimitive(sm.proxyPort.first()))
        put("proxyUsername", JsonPrimitive(sm.proxyUsername.first()))
        if (includeApiKeys) put("proxyPassword", JsonPrimitive(sm.proxyPassword.first()))
        put("proxyBypass", JsonPrimitive(sm.proxyBypass.first()))

        val aliases = sm.modelAliases.first()
        if (aliases.isNotEmpty()) {
            putJsonObject("modelAliases") {
                aliases.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }
        }
    }

    suspend fun restoreFromJsonObject(obj: JsonObject, sm: SettingsManager) {
        // System prompts are restored standalone from system_prompts.json — NOT duplicated here.
        obj["imageTranscriptionEnabledModels"]?.jsonPrimitive?.contentOrNull?.let {
            val set = it.split(",").filter { s -> s.isNotBlank() }.toSet()
            sm.saveImageTranscriptionEnabledModels(set)
        }
        obj["imageTranscriptionModel"]?.jsonPrimitive?.contentOrNull?.let { sm.saveImageTranscriptionModel(it) }
        obj["imageTranscriptionBatchSize"]?.jsonPrimitive?.int?.let { sm.saveImageTranscriptionBatchSize(it) }
        obj["imageTranscriptionPrompt"]?.jsonPrimitive?.contentOrNull?.let { sm.saveImageTranscriptionPrompt(it) }
        obj["webSearchNumResults"]?.jsonPrimitive?.int?.let { sm.saveWebSearchNumResults(it) }
        obj["searchContextWindow"]?.jsonPrimitive?.int?.let { sm.saveSearchContextWindow(it) }
        obj["searchMatchLimit"]?.jsonPrimitive?.int?.let { sm.saveSearchMatchLimit(it) }
        obj["defaultTemperature"]?.jsonPrimitive?.float?.let { sm.saveDefaultTemperature(it) }
        obj["defaultMaxTokens"]?.jsonPrimitive?.int?.let { sm.saveDefaultMaxTokens(it) }
        obj["defaultTopP"]?.jsonPrimitive?.float?.let { sm.saveDefaultTopP(it) }
        obj["defaultFrequencyPenalty"]?.jsonPrimitive?.float?.let { sm.saveDefaultFrequencyPenalty(it) }
        obj["defaultPresencePenalty"]?.jsonPrimitive?.float?.let { sm.saveDefaultPresencePenalty(it) }
        obj["conversationSettings"]?.jsonObject?.forEach { (convId, settingsJson) ->
            val s = settingsJson.jsonObject
            val legacyBudgetTokens = ThinkingLevels.legacyBudgetTokens(s["thinkingLevel"]?.jsonPrimitive?.contentOrNull)
            val cs = ConversationSettings(
                contextWindow = s["contextWindow"]?.jsonPrimitive?.int,
                temperature = s["temperature"]?.jsonPrimitive?.float,
                maxTokens = s["maxTokens"]?.jsonPrimitive?.int,
                topP = s["topP"]?.jsonPrimitive?.float,
                frequencyPenalty = s["frequencyPenalty"]?.jsonPrimitive?.float,
                presencePenalty = s["presencePenalty"]?.jsonPrimitive?.float,
                codeExecutionEnabled = s["codeExecutionEnabled"]?.jsonPrimitive?.boolean,
                googleSearchEnabled = s["googleSearchEnabled"]?.jsonPrimitive?.boolean,
                thinkingEnabled = s["thinkingEnabled"]?.jsonPrimitive?.boolean,
                thinkingLevel = s["thinkingLevel"]?.jsonPrimitive?.contentOrNull?.let { ThinkingLevels.normalize(it) },
                thinkingBudgetEnabled = s["thinkingBudgetEnabled"]?.jsonPrimitive?.boolean
                    ?: legacyBudgetTokens?.let { true },
                thinkingBudgetTokens = s["thinkingBudgetTokens"]?.jsonPrimitive?.int ?: legacyBudgetTokens,
                webSearchEnabled = s["webSearchEnabled"]?.jsonPrimitive?.boolean,
                shellEnabled = s["shellEnabled"]?.jsonPrimitive?.boolean
            )
            if (!cs.isAllNull()) sm.saveConversationSettings(convId, cs)
        }
        obj["showDocumentationFab"]?.jsonPrimitive?.boolean?.let { sm.saveShowDocumentationFab(it) }
        obj["proxyEnabled"]?.jsonPrimitive?.boolean?.let { sm.saveProxyEnabled(it) }
        obj["proxyType"]?.jsonPrimitive?.contentOrNull?.let { sm.saveProxyType(it) }
        obj["proxyHost"]?.jsonPrimitive?.contentOrNull?.let { sm.saveProxyHost(it) }
        obj["proxyPort"]?.jsonPrimitive?.contentOrNull?.let { sm.saveProxyPort(it) }
        obj["proxyUsername"]?.jsonPrimitive?.contentOrNull?.let { sm.saveProxyUsername(it) }
        obj["proxyPassword"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) sm.saveProxyPassword(it) }
        obj["proxyBypass"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) sm.saveProxyBypass(it) }
        obj["themeMode"]?.jsonPrimitive?.contentOrNull?.let { sm.saveThemeMode(it) }
        obj["colorScheme"]?.jsonPrimitive?.contentOrNull?.let { sm.saveColorScheme(it) }
        obj["dynamicColor"]?.jsonPrimitive?.boolean?.let { sm.saveDynamicColor(it) }
        obj["blurEffectsEnabled"]?.jsonPrimitive?.boolean?.let { sm.saveBlurEffectsEnabled(it) }
        obj["hapticsEnabled"]?.jsonPrimitive?.boolean?.let { sm.saveHapticsEnabled(it) }
        obj["schemeStyle"]?.jsonPrimitive?.contentOrNull?.let { sm.saveSchemeStyle(it) }
        obj["fontPreference"]?.jsonPrimitive?.contentOrNull?.let { sm.saveFontPreference(it) }
        obj["customFontPath"]?.jsonPrimitive?.contentOrNull?.let { sm.saveCustomFontPath(it) }
        obj["customFontName"]?.jsonPrimitive?.contentOrNull?.let { sm.saveCustomFontName(it) }
        obj["autoUpdateCheck"]?.jsonPrimitive?.boolean?.let { sm.saveAutoUpdateCheck(it) }

        obj["modelAliases"]?.jsonObject?.let { aliasesObj ->
            val map = aliasesObj.mapNotNull { (k, v) ->
                v.jsonPrimitive?.contentOrNull?.let { k to it }
            }.toMap()
            if (map.isNotEmpty()) sm.saveModelAliases(map)
        }
    }
}
