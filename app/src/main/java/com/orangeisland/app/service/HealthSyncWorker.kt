package com.orangeisland.app.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.gadgetbridge.GadgetbridgeReader
import com.orangeisland.app.data.service.HealthSyncAppUsageData
import com.orangeisland.app.data.service.HealthSyncData
import com.orangeisland.app.data.service.HealthSyncHealthData
import com.orangeisland.app.data.service.HealthSyncLocationData
import com.orangeisland.app.data.service.HealthSyncNotificationData
import com.orangeisland.app.data.service.HealthSyncSupabaseClient
import com.orangeisland.app.tool.device.DeviceNotificationListenerService
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.trigger.AppForegroundDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HealthSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLog.d(TAG, "HealthSyncWorker started")
        val settingsManager = SettingsManager(applicationContext)

        val healthSyncEnabled = try {
            settingsManager.healthSyncEnabled.first()
        } catch (_: Exception) { false }
        val supabaseUrl = try {
            settingsManager.healthSyncSupabaseUrl.first()
        } catch (_: Exception) { "" }
        val supabaseKey = try {
            settingsManager.healthSyncSupabaseApiKey.first()
        } catch (_: Exception) { "" }
        val tableName = try {
            settingsManager.healthSyncTableName.first()
        } catch (_: Exception) { "device_data" }

        if (!healthSyncEnabled || supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            DebugLog.d(TAG, "Health sync disabled or not configured, skipping")
            return Result.success()
        }

        val gadgetbridgeEnabled = try {
            settingsManager.gadgetbridgeEnabled.first()
        } catch (_: Exception) { false }
        val gadgetbridgeDbPath = try {
            settingsManager.gadgetbridgeDbPath.first()
        } catch (_: Exception) { "" }
        val amapApiKey = try {
            settingsManager.amapApiKey.first()
        } catch (_: Exception) { "" }

        return try {
            val syncData = collectData(
                context = applicationContext,
                settingsManager = settingsManager,
                gadgetbridgeEnabled = gadgetbridgeEnabled,
                gadgetbridgeDbPath = gadgetbridgeDbPath,
                amapApiKey = amapApiKey
            )

            val client = HealthSyncSupabaseClient(supabaseUrl, supabaseKey, tableName)
            val result = client.insert(syncData)

            if (result.isSuccess) {
                DebugLog.d(TAG, "Health sync uploaded successfully")
                Result.success()
            } else {
                DebugLog.e(TAG, "Health sync upload failed", result.exceptionOrNull() ?: Exception("unknown error"))
                Result.retry()
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Health sync unexpected error", e)
            Result.retry()
        }
    }

    private suspend fun collectData(
        context: Context,
        settingsManager: SettingsManager,
        gadgetbridgeEnabled: Boolean,
        gadgetbridgeDbPath: String,
        amapApiKey: String,
    ): HealthSyncData {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        // ── Location ──
        var locationData: HealthSyncLocationData? = null
        try {
            if (hasLocationPermission(context)) {
                val loc = lastKnownLocation(context)
                if (loc != null) {
                    var address = ""
                    var city = ""
                    var district = ""
                    var street = ""
                    if (amapApiKey.isNotBlank()) {
                        val regeo = amapReverseGeocode(amapApiKey, loc.latitude, loc.longitude)
                        if (regeo != null) {
                            address = regeo.formatted
                            city = regeo.city
                            district = regeo.district
                            street = regeo.street
                        }
                    }
                    locationData = HealthSyncLocationData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        address = address,
                        city = city,
                        district = district,
                        street = street
                    )
                }
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to collect location", e)
        }

        // ── App usage ──
        var appUsageData = emptyList<HealthSyncAppUsageData>()
        try {
            if (hasUsageStatsPermission(context)) {
                val usm = context.getSystemService(UsageStatsManager::class.java)
                if (usm != null) {
                    val now = System.currentTimeMillis()
                    val start = midnightToday()
                    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: emptyList()
                    val pm = context.packageManager
                    appUsageData = stats.asSequence()
                        .filter { it.packageName != null }
                        .groupBy { it.packageName }
                        .map { (pkg, list) ->
                            val totalMs = list.sumOf { it.totalTimeInForeground }
                            val lastUsed = list.maxOf { it.lastTimeUsed }
                            val label = runCatching {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            }.getOrDefault(pkg)
                            HealthSyncAppUsageData(
                                packageName = pkg,
                                appName = label,
                                totalTimeInForeground = totalMs,
                                lastTimeUsed = lastUsed
                            )
                        }
                        .filter { it.totalTimeInForeground > 0 }
                        .sortedByDescending { it.totalTimeInForeground }
                        .take(30)
                        .toList()
                }
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to collect app usage", e)
        }

        // ── Notifications ──
        var notificationData = emptyList<HealthSyncNotificationData>()
        try {
            if (DeviceNotificationListenerService.companionActive) {
                val pm = context.packageManager
                notificationData = DeviceNotificationListenerService.snapshot()
                    .take(50)
                    .map { n ->
                        val label = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString()
                        }.getOrDefault(n.packageName)
                        HealthSyncNotificationData(
                            packageName = n.packageName,
                            appName = label,
                            title = n.title,
                            content = n.text,
                            timestamp = n.postTime,
                            category = null
                        )
                    }
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to collect notifications", e)
        }

        // ── Foreground app ──
        var foregroundApp = ""
        try {
            val lastForeground = AppForegroundDispatcher.lastKnown
            if (!lastForeground.isNullOrBlank()) {
                foregroundApp = lastForeground
            } else if (hasUsageStatsPermission(context)) {
                val usm = context.getSystemService(UsageStatsManager::class.java)
                val now = System.currentTimeMillis()
                val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
                foregroundApp = stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to get foreground app", e)
        }

        // ── Health data ──
        var healthData: HealthSyncHealthData? = null
        try {
            if (gadgetbridgeEnabled && GadgetbridgeReader.dbFileExists(gadgetbridgeDbPath)) {
                val dailySummaries = GadgetbridgeReader.readDailySummaries(7, gadgetbridgeDbPath)
                val todaySummary = dailySummaries.lastOrNull()
                val latestActivity = GadgetbridgeReader.readLatestActivitySample(gadgetbridgeDbPath)
                val (latestSpo2, latestStress) = GadgetbridgeReader.readLatestSpo2AndStress(gadgetbridgeDbPath)
                val latestSleep = GadgetbridgeReader.readSleepSummaries(7, gadgetbridgeDbPath).firstOrNull()

                healthData = HealthSyncHealthData(
                    heartRate = latestActivity?.heartRate,
                    stepsToday = todaySummary?.steps,
                    caloriesToday = todaySummary?.calories,
                    hrRestingToday = todaySummary?.hrResting,
                    hrMaxToday = todaySummary?.hrMax,
                    hrMinToday = todaySummary?.hrMin,
                    hrAvgToday = todaySummary?.hrAvg,
                    spo2 = latestSpo2,
                    spo2AvgToday = todaySummary?.spo2Avg,
                    stress = latestStress,
                    stressAvgToday = todaySummary?.stressAvg,
                    sleepStartMs = latestSleep?.timestamp,
                    sleepWakeupMs = latestSleep?.wakeupTime,
                    sleepTotalMinutes = latestSleep?.totalDuration,
                    sleepDeepMinutes = latestSleep?.deepSleep,
                    sleepLightMinutes = latestSleep?.lightSleep,
                    sleepRemMinutes = latestSleep?.remSleep,
                )
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to collect health data", e)
        }

        return HealthSyncData(
            timestamp = timestamp,
            foregroundApp = foregroundApp,
            location = locationData,
            appUsage = appUsageData,
            notifications = notificationData,
            health = healthData
        )
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val mode = (context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager)
                .unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun lastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return try {
            @Suppress("MissingPermission")
            lm.getLastKnownLocation(provider)
        } catch (_: SecurityException) { null } catch (_: Exception) { null }
    }

    private data class AmapAddr(val formatted: String, val city: String, val district: String, val street: String)

    private fun amapReverseGeocode(apiKey: String, lat: Double, lng: Double): AmapAddr? {
        val url = "https://restapi.amap.com/v3/geocode/regeo?" +
            "key=${Uri.encode(apiKey)}" +
            "&location=${lng},${lat}" +
            "&extensions=all" +
            "&output=JSON"
        return try {
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = Json { ignoreUnknownKeys = true }
            val resp = json.parseToJsonElement(body) as? JsonObject ?: return null
            if ((resp["status"] as? JsonPrimitive)?.content != "1") return null
            val regeo = resp["regeocode"] as? JsonObject ?: return null
            val comp = regeo["addressComponent"] as? JsonObject
            val street = (comp?.get("streetNumber") as? JsonObject)?.let { streetObj ->
                (streetObj["street"] as? JsonPrimitive)?.content ?: ""
            } ?: (comp?.get("township") as? JsonPrimitive)?.content ?: ""
            AmapAddr(
                formatted = (regeo["formatted_address"] as? JsonPrimitive)?.content ?: "",
                city = (comp?.get("city") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: "",
                district = (comp?.get("district") as? JsonPrimitive)?.content ?: "",
                street = street
            )
        } catch (e: Exception) {
            DebugLog.w(TAG, "Amap reverse geocode failed", e)
            null
        }
    }

    private fun midnightToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "HealthSyncWorker"
        private const val WORK_NAME = "health_sync_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            DebugLog.d(TAG, "Scheduled health sync worker")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            DebugLog.d(TAG, "Cancelled health sync worker")
        }
    }
}
