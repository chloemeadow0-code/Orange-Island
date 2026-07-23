package com.orangeisland.app.data.service

import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class HealthSyncData(
    val timestamp: String,
    val foregroundApp: String = "",
    val location: HealthSyncLocationData? = null,
    val appUsage: List<HealthSyncAppUsageData> = emptyList(),
    val notifications: List<HealthSyncNotificationData> = emptyList(),
    val deviceEvent: String? = null,
    val health: HealthSyncHealthData? = null,
)

@Serializable
data class HealthSyncLocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
)

@Serializable
data class HealthSyncAppUsageData(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
)

@Serializable
data class HealthSyncNotificationData(
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val category: String? = null,
)

@Serializable
data class HealthSyncHealthData(
    val heartRate: Int? = null,
    val stepsToday: Int? = null,
    val caloriesToday: Int? = null,
    val hrRestingToday: Int? = null,
    val hrMaxToday: Int? = null,
    val hrMinToday: Int? = null,
    val hrAvgToday: Int? = null,
    val spo2: Int? = null,
    val spo2AvgToday: Int? = null,
    val stress: Int? = null,
    val stressAvgToday: Int? = null,
    val sleepStartMs: Long? = null,
    val sleepWakeupMs: Long? = null,
    val sleepTotalMinutes: Int? = null,
    val sleepDeepMinutes: Int? = null,
    val sleepLightMinutes: Int? = null,
    val sleepRemMinutes: Int? = null,
)

class HealthSyncSupabaseClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val tableName: String,
) {
    companion object {
        private const val TAG = "HealthSyncSupabase"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun insert(data: HealthSyncData): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                throw IllegalArgumentException("Supabase URL and API Key must not be blank")
            }

            val url = URL("${baseUrl.trimEnd('/')}/rest/v1/$tableName")
            val jsonObject = buildJsonObject(data)
            val jsonString = json.encodeToString(JsonObject.serializer(), jsonObject)

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Prefer", "return=minimal")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(jsonString)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("Supabase API error ($responseCode): $errorBody")
            }

            UsageLogManager.log(
                UsageLogManager.Type.SYNC,
                "health_sync_insert",
                "table=$tableName, hasLocation=${data.location != null}, hasHealth=${data.health != null}, apps=${data.appUsage.size}, notifications=${data.notifications.size}"
            )
            DebugLog.d(TAG, "Successfully inserted row into $tableName")
        }
    }

    private fun buildJsonObject(data: HealthSyncData): JsonObject {
        val map = mutableMapOf<String, JsonPrimitive>()
        map["timestamp"] = JsonPrimitive(data.timestamp)
        map["foreground_app"] = JsonPrimitive(data.foregroundApp)

        data.location?.let { loc ->
            map["location_latitude"] = JsonPrimitive(loc.latitude)
            map["location_longitude"] = JsonPrimitive(loc.longitude)
            map["location_address"] = JsonPrimitive(loc.address)
            map["location_city"] = JsonPrimitive(loc.city)
            map["location_district"] = JsonPrimitive(loc.district)
            map["location_street"] = JsonPrimitive(loc.street)
        }

        if (data.appUsage.isNotEmpty()) {
            val appUsageJson = json.encodeToString(
                kotlinx.serialization.serializer<List<HealthSyncAppUsageData>>(),
                data.appUsage
            )
            map["app_usage"] = JsonPrimitive(appUsageJson)
        }

        if (data.notifications.isNotEmpty()) {
            val notificationsJson = json.encodeToString(
                kotlinx.serialization.serializer<List<HealthSyncNotificationData>>(),
                data.notifications
            )
            map["notifications"] = JsonPrimitive(notificationsJson)
        }

        data.deviceEvent?.let { event ->
            map["device_event"] = JsonPrimitive(event)
        }

        data.health?.let { h ->
            val healthJson = json.encodeToString(HealthSyncHealthData.serializer(), h)
            map["health_data"] = JsonPrimitive(healthJson)
        }

        return JsonObject(map)
    }
}
