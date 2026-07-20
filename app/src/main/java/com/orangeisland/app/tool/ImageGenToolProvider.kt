package com.orangeisland.app.tool

import android.app.Application
import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.api.ProviderDefaults
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * Tool that generates images from a text prompt via an OpenAI-compatible
 * `/images/generations` endpoint (BYOK). The decoded image is written to
 * filesDir as `img_<uuid>.jpg` and its path is collected in [pending] so the
 * GenerationManager can attach it to the model message for inline display.
 */
class ImageGenToolProvider(private val app: Application) : ToolProvider {

    /** File paths of images produced since the last [drainImages]. Thread-safe. */
    private val pending = java.util.Collections.synchronizedList(mutableListOf<String>())

    /** Atomically take and clear the images generated so far. */
    fun drainImages(): List<String> = synchronized(pending) {
        val copy = pending.toList()
        pending.clear()
        copy
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.imageGenEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "generate_image",
                description = "Generate an image from a text prompt. The generated image is shown to the user automatically — do NOT attempt to embed or describe the raw image data. Use this whenever the user asks to create, draw, paint, or generate a picture.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "prompt" to ToolProperty("string", "A detailed description of the image to generate."),
                        "size" to ToolProperty("string", "Optional image size, e.g. 1024x1024, 1024x1536, or 1536x1024.")
                    ),
                    required = listOf("prompt")
                )
            ))
        )
    }

    override fun handles(name: String): Boolean = name == "generate_image"

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        val argsStr = arguments.ifBlank { "{}" }
        val args = try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        } catch (_: Exception) { emptyMap() }
        val prompt = (args["prompt"] as? JsonPrimitive)?.content
            ?: return err("no_prompt", null)
        val size = (args["size"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: ctx.imageGenSize.ifBlank { "1024x1024" }

        val apiKey = ctx.imageGenApiKey
        if (apiKey.isBlank()) return err("no_api_key", null)
        val baseUrl = ctx.imageGenBaseUrl.ifBlank { ProviderDefaults.OPENAI_BASE_URL }.trimEnd('/')
        val model = ctx.imageGenModel.ifBlank { "gpt-image-1" }

        return withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject {
                    put("model", model)
                    put("prompt", prompt)
                    put("size", size)
                    put("n", 1)
                }.toString()
                val response = HttpClient.post(
                    "$baseUrl/images/generations",
                    body,
                    mapOf("Authorization" to "Bearer $apiKey")
                ) ?: return@withContext err("no_response", null)

                val json = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response)
                val first = json["data"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withContext err("no_image", "The endpoint returned no image data.")

                val bytes: ByteArray = run {
                    val b64 = (first["b64_json"] as? JsonPrimitive)?.content
                    if (!b64.isNullOrBlank()) {
                        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    } else {
                        val url = (first["url"] as? JsonPrimitive)?.content
                            ?: return@withContext err("no_image", "No b64_json or url in the response.")
                        HttpClient.getBytes(url) ?: return@withContext err("download_failed", null)
                    }
                }

                val file = File(app.filesDir, "img_${UUID.randomUUID()}.jpg")
                file.outputStream().use { it.write(bytes) }
                pending.add(file.absolutePath)

                buildJsonObject {
                    put("type", "image_generation")
                    put("status", "ok")
                    put("size", size)
                }.toString()
            } catch (e: Exception) {
                DebugLog.e("ImageGenTool", "generate_image failed", e)
                err("generation_error", e.message)
            }
        }
    }

    private fun err(code: String, message: String?): String = buildJsonObject {
        put("type", "image_generation")
        put("error", code)
        if (!message.isNullOrBlank()) put("message", message)
    }.toString()
}
