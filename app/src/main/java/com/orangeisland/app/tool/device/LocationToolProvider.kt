package com.orangeisland.app.tool.device

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import com.orangeisland.app.api.HttpClient
import com.orangeisland.app.api.ToolDefinition
import com.orangeisland.app.api.ToolFunction
import com.orangeisland.app.api.ToolParameters
import com.orangeisland.app.api.ToolProperty
import com.orangeisland.app.tool.SensitiveToolApprovalGate
import com.orangeisland.app.tool.ToolProvider
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.viewmodel.GenerationContext
import com.orangeisland.app.viewmodel.PermissionController
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Location tool backed by the platform [LocationManager] (no Google Play Services — keeps the
 * fdroid flavor clean) and Amap (高德) REST for reverse geocoding + nearby-POI search.
 *
 * Two tools:
 *  - [get_current_location] — last-known GPS coordinates reverse-geocoded to a Chinese address.
 *  - [explore_nearby] — points of interest around the current location within a radius
 *    (default 1000m), e.g. restaurants, ATMs, convenience stores.
 *
 * Requires the user to fill an Amap Web Service API key in the settings page (locationEnabled
 * is meaningless without it). At execute time the tool checks both the runtime permission and
 * the key; a missing one returns a JSON error rather than throwing.
 */
class LocationToolProvider(
    private val app: Application,
    private val permissionController: PermissionController,
    private val approvalGate: SensitiveToolApprovalGate? = null,
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.locationEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "get_current_location",
                description = "Get the user's current GPS location reverse-geocoded to a " +
                    "street address (China). Returns latitude, longitude, and a formatted " +
                    "address. Use when the user asks 'where am I', '我的位置', or needs their " +
                    "current location for context.",
                parameters = ToolParameters(properties = emptyMap(), required = emptyList())
            )),
            ToolDefinition(function = ToolFunction(
                name = "explore_nearby",
                description = "Find points of interest near the user's current location within " +
                    "a radius (default 1000 meters). Useful for 'what's around me', '附近有什么', " +
                    "'find a restaurant nearby'. Returns name, address, distance, category, and " +
                    "coordinates for each POI.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "keyword" to ToolProperty("string", "Search keyword, e.g. '餐厅', 'ATM', '便利店', 'hospital'."),
                        "radius" to ToolProperty("integer", "Search radius in meters (default 1000, max 3000)."),
                        "limit" to ToolProperty("integer", "Max number of results to return (default 10, max 20).")
                    ),
                    required = listOf("keyword")
                )
            ))
        )
    }

    override suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String {
        if (!permissionController.isGranted(PermissionController.Tool.LOCATION)) {
            return error("permission_denied",
                "Location permission not granted. Ask the user to enable Location in Settings → Device Access.")
        }
        if (ctx.amapApiKey.isBlank()) {
            return error("no_api_key",
                "Amap API key not configured. Ask the user to fill it in Settings → Device Access → Location.")
        }
        val desc = when (name) {
            "get_current_location" -> "获取当前 GPS 坐标并逆地理编码为地址"
            "explore_nearby" -> "基于当前位置搜索附近 POI"
            else -> name
        }
        if (approvalGate?.approval?.invoke(name, desc) == false) {
            return error("approval_denied", "用户拒绝了定位工具调用请求。")
        }
        return when (name) {
            "get_current_location" -> currentLocation(ctx.amapApiKey)
            "explore_nearby" -> exploreNearby(arguments, ctx.amapApiKey)
            else -> unknownTool(name)
        }
    }

    override fun handles(name: String): Boolean = name in setOf("get_current_location", "explore_nearby")

    // ── Internals ─────────────────────────────────────────────

    private fun currentLocation(apiKey: String): String {
        val loc = lastKnownLocation() ?: return error("no_location",
            "No last-known location available. Ask the user to open a maps app once to refresh it.")
        val (lat, lng) = loc
        val addr = amapRegeo(apiKey, lat, lng)
        return if (addr != null) buildJsonObject {
            put("latitude", lat)
            put("longitude", lng)
            put("formatted_address", addr.formatted)
            put("province", addr.province)
            put("city", addr.city)
            put("district", addr.district)
        }.toString()
        else buildJsonObject {
            put("latitude", lat); put("longitude", lng)
            put("address", "unknown (reverse geocode failed)")
        }.toString()
    }

    private fun exploreNearby(arguments: String, apiKey: String): String {
        val parsed = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments.ifBlank { "{}" })
        val keyword = (parsed["keyword"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: return error("no_keyword", "Missing 'keyword'.")
        val radius = ((parsed["radius"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1000).coerceIn(1, 3000)
        val limit = ((parsed["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 10).coerceIn(1, 20)

        val loc = lastKnownLocation() ?: return error("no_location",
            "No last-known location available. Ask the user to open a maps app once to refresh it.")
        val (lat, lng) = loc

        val url = "https://restapi.amap.com/v3/place/around?" +
            "key=${Uri.encode(apiKey)}" +
            "&location=${lng},${lat}" +
            "&keywords=${Uri.encode(keyword)}" +
            "&radius=$radius" +
            "&offset=$limit" +
            "&page=1" +
            "&extensions=base" +
            "&output=JSON"
        val body = HttpClient.get(url) ?: return error("request_failed", "Amap around search returned no response.")
        return try {
            val resp = json.parseToJsonElement(body) as? JsonObject
                ?: return error("parse_error", "Amap response was not a JSON object.")
            if ((resp["status"] as? JsonPrimitive)?.content != "1") {
                val info = (resp["info"] as? JsonPrimitive)?.content ?: "unknown"
                return error("amap_error", "Amap error: $info")
            }
            val pois = resp["pois"] as? JsonArray
                ?: return buildJsonObject {
                    put("keyword", keyword); put("radius_meters", radius)
                    put("latitude", lat); put("longitude", lng)
                    put("places", buildJsonArray {})
                }.toString()
            val places = buildJsonArray {
                pois.forEach { entry ->
                    val poi = entry as? JsonObject ?: return@forEach
                    val name = (poi["name"] as? JsonPrimitive)?.content ?: ""
                    val address = (poi["address"] as? JsonPrimitive)?.content ?: ""
                    val type = (poi["type"] as? JsonPrimitive)?.content ?: ""
                    val dist = (poi["distance"] as? JsonPrimitive)?.content ?: ""
                    val locStr = (poi["location"] as? JsonPrimitive)?.content ?: ""
                    val (lng2, lat2) = locStr.split(",").let { if (it.size >= 2) it[0] to it[1] else "" to "" }
                    add(buildJsonObject {
                        put("name", name); put("address", address); put("category", type)
                        put("distance_meters", dist); put("longitude", lng2); put("latitude", lat2)
                    })
                }
            }
            buildJsonObject {
                put("keyword", keyword)
                put("radius_meters", radius)
                put("latitude", lat); put("longitude", lng)
                put("places", places)
            }.toString()
        } catch (e: Exception) {
            DebugLog.e("LocationTool", "explore_nearby parse failed", e)
            error("parse_error", "Failed to parse Amap response: ${e.message}")
        }
    }

    /** Returns [lat, lng] or null if unavailable. */
    private fun lastKnownLocation(): Pair<Double, Double>? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return try {
            @Suppress("MissingPermission")
            val loc: Location? = lm.getLastKnownLocation(provider)
            loc?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) { null } catch (e: Exception) { null }
    }

    private data class AmapAddr(val formatted: String, val province: String, val city: String, val district: String)

    private fun amapRegeo(apiKey: String, lat: Double, lng: Double): AmapAddr? {
        val url = "https://restapi.amap.com/v3/geocode/regeo?" +
            "key=${Uri.encode(apiKey)}" +
            "&location=${lng},${lat}" +
            "&extensions=base" +
            "&output=JSON"
        val body = HttpClient.get(url) ?: return null
        return try {
            val resp = json.parseToJsonElement(body) as? JsonObject ?: return null
            if ((resp["status"] as? JsonPrimitive)?.content != "1") return null
            val regeo = resp["regeocode"] as? JsonObject ?: return null
            val comp = regeo["addressComponent"] as? JsonObject
            AmapAddr(
                formatted = (regeo["formatted_address"] as? JsonPrimitive)?.content ?: "",
                province = (comp?.get("province") as? JsonPrimitive)?.content ?: "",
                city = (comp?.get("city") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: "",
                district = (comp?.get("district") as? JsonPrimitive)?.content ?: ""
            )
        } catch (e: Exception) {
            DebugLog.e("LocationTool", "regeo parse failed", e); null
        }
    }

    private fun error(type: String, message: String): String =
        buildJsonObject { put("error", type); put("message", message) }.toString()

    private fun unknownTool(name: String): String =
        buildJsonObject { put("error", "unknown_tool"); put("name", name) }.toString()
}
