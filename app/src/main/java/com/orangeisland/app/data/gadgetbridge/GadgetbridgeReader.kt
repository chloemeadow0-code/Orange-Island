package com.orangeisland.app.data.gadgetbridge

import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import com.orangeisland.app.util.DebugLog
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "GadgetbridgeReader"

object GadgetbridgeDbPath {
    /** 使用 Environment API 获取主路径 */
    val DB_PATH: String
        get() = File(
            Environment.getExternalStorageDirectory(),
            "Download/手环/Gadgetbridge.db"
        ).absolutePath

    /**
     * 获取所有可能的路径变体列表
     * @param customPath 用户自定义路径，为空则使用默认路径列表
     */
    fun getPossiblePaths(customPath: String = ""): List<String> {
        val paths = mutableListOf<String>()

        // 如果有自定义路径，优先使用
        if (customPath.isNotBlank()) {
            paths.add(customPath)
        }

        // 默认路径列表（同时包含 .db 和 .sqlite3 变体）
        val defaultPaths = listOf(
            DB_PATH,
            "/sdcard/Download/手环/Gadgetbridge.db",
            "/storage/emulated/0/Download/手环/Gadgetbridge.db",
            "/sdcard/下载/手环/Gadgetbridge.db",
            "/storage/emulated/0/下载/手环/Gadgetbridge.db",
            File(Environment.getExternalStorageDirectory(), "下载/手环/Gadgetbridge.db").absolutePath,
        )
        paths.addAll(defaultPaths)

        // 为每个 .db 路径添加 .sqlite3 变体
        val sqlite3Variants = paths
            .filter { it.endsWith(".db") }
            .map { it.removeSuffix(".db") + ".sqlite3" }
        paths.addAll(sqlite3Variants)

        return paths.distinct()
    }
}

object GadgetbridgeReader {

    // 缓存上次找到的路径，避免重复搜索
    private var cachedDbPath: String? = null

    fun dbFileExists(customPath: String = ""): Boolean {
        val paths = GadgetbridgeDbPath.getPossiblePaths(customPath)
        for (path in paths) {
            try {
                val file = File(path)
                DebugLog.d(TAG, "检查数据库文件: $path, exists=${file.exists()}, length=${if (file.exists()) file.length() else 0}")
                if (file.exists() && file.length() > 0) {
                    cachedDbPath = path
                    return true
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "检查数据库文件失败: $path", e)
            }
        }
        return false
    }

    /**
     * 获取实际存在的数据库文件路径
     */
    private fun findDbPath(customPath: String = ""): String? {
        // 如果有缓存的路径且文件仍存在，直接返回
        cachedDbPath?.let { cached ->
            if (File(cached).exists() && File(cached).length() > 0) {
                return cached
            }
            cachedDbPath = null
        }

        val paths = GadgetbridgeDbPath.getPossiblePaths(customPath)
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    DebugLog.d(TAG, "找到数据库文件: $path")
                    cachedDbPath = path
                    return path
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun <T> withDatabase(customPath: String = "", block: (SQLiteDatabase) -> T): Result<T> {
        val dbPath = findDbPath(customPath) ?: return Result.failure(
            IllegalStateException("Gadgetbridge 数据库文件不存在")
        )
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            Result.success(block(db))
        } catch (e: Exception) {
            DebugLog.e(TAG, "打开数据库失败: $dbPath", e)
            Result.failure(e)
        } finally {
            db?.close()
        }
    }

    // ==================== 厂商路由 ====================

    /**
     * 设备厂商（用于按厂商分流到不同的协议表实现）
     */
    private enum class Manufacturer { XIAOMI, HUAWEI }

    /**
     * 查询 DEVICE 表最新一条记录的 MANUFACTURER 字段判断当前设备厂商。
     *
     * - 取最新一条 DEVICE 记录（按 _id DESC，兼容老库无排序字段的情况）。
     * - MANUFACTURER 字段不区分大小写包含 "huawei" 即判定为华为。
     * - 查询异常 / 表不存在 / 字段为空 / 无法识别的厂商：默认走小米逻辑，保证向后兼容。
     */
    private fun detectManufacturer(db: SQLiteDatabase): Manufacturer {
        return try {
            // DEVICE 表的 MANUFACTURER 字段实测值如 "Huawei"
            val cursor = db.query(
                "DEVICE",
                arrayOf("MANUFACTURER"),
                null, null, null, null,
                "_id DESC", "1"
            )
            cursor.use {
                if (!it.moveToFirst()) {
                    DebugLog.w(TAG, "厂商判断: DEVICE 表为空, 默认走小米逻辑")
                    Manufacturer.XIAOMI
                } else {
                    val manufacturer = it.getString(0)?.lowercase()?.trim().orEmpty()
                    DebugLog.d(TAG, "厂商判断: MANUFACTURER=$manufacturer")
                    when {
                        manufacturer.contains("huawei") -> Manufacturer.HUAWEI
                        // 未识别的厂商一律走小米，保持向后兼容
                        else -> Manufacturer.XIAOMI
                    }
                }
            }
        } catch (e: Exception) {
            // DEVICE 表不存在或字段缺失时不阻断主流程
            DebugLog.e(TAG, "厂商判断失败, 默认走小米逻辑", e)
            Manufacturer.XIAOMI
        }
    }

    // ==================== 公开方法（4 个，签名保持不变，内部按厂商分流） ====================

    fun readDailySummaries(days: Int, customPath: String = ""): List<DailySummary> {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readDailySummariesHuawei(db, days)
                Manufacturer.XIAOMI -> readDailySummariesXiaomi(db, days)
            }
        }.getOrDefault(emptyList())
    }

    fun readLatestActivitySample(customPath: String = ""): ActivitySample? {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readLatestActivitySampleHuawei(db)
                Manufacturer.XIAOMI -> readLatestActivitySampleXiaomi(db)
            }
        }.getOrDefault(null)
    }

    fun readSleepSummaries(days: Int, customPath: String = ""): List<SleepSummary> {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readSleepSummariesHuawei(db, days)
                Manufacturer.XIAOMI -> readSleepSummariesXiaomi(db, days)
            }
        }.getOrDefault(emptyList())
    }

    fun readLatestSpo2AndStress(customPath: String = ""): Pair<Int?, Int?> {
        return withDatabase(customPath) { db ->
            when (detectManufacturer(db)) {
                Manufacturer.HUAWEI -> readLatestSpo2AndStressHuawei(db)
                Manufacturer.XIAOMI -> readLatestSpo2AndStressXiaomi(db)
            }
        }.getOrDefault(null to null)
    }

    // ==================== 小米实现（原样保留，一行未改） ====================

    private fun readDailySummariesXiaomi(db: SQLiteDatabase, days: Int): List<DailySummary> {
        val now = LocalDate.now()
        val startTime = now.minusDays(days.toLong())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val summaries = mutableListOf<DailySummary>()
        val cursor = db.query(
            "XIAOMI_DAILY_SUMMARY_SAMPLE",
            arrayOf("TIMESTAMP", "STEPS", "HR_RESTING", "HR_MAX", "HR_MIN", "HR_AVG", "STRESS_AVG", "CALORIES", "SPO2_AVG"),
            "TIMESTAMP >= ?",
            arrayOf(startTime.toString()),
            null, null, "TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val timestamp = it.getLong(0)
                val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                summaries.add(DailySummary(timestamp, date, it.getInt(1), getIntOrNull(it, 2), getIntOrNull(it, 3), getIntOrNull(it, 4), getIntOrNull(it, 5), getIntOrNull(it, 6), getIntOrNull(it, 7), getIntOrNull(it, 8)))
            }
        }
        return summaries
    }

    private fun readLatestActivitySampleXiaomi(db: SQLiteDatabase): ActivitySample? {
        val cursor = db.query("XIAOMI_ACTIVITY_SAMPLE", arrayOf("TIMESTAMP", "HEART_RATE", "STEPS", "STRESS", "SPO2", "RAW_INTENSITY"), "HEART_RATE IS NOT NULL AND HEART_RATE > 0", null, null, null, "TIMESTAMP DESC", "1")
        cursor.use {
            return if (it.moveToFirst()) ActivitySample(it.getLong(0), getIntOrNull(it, 1), getIntOrNull(it, 2), getIntOrNull(it, 3), getIntOrNull(it, 4), getIntOrNull(it, 5)) else null
        }
    }

    private fun readSleepSummariesXiaomi(db: SQLiteDatabase, days: Int): List<SleepSummary> {
        val now = System.currentTimeMillis()
        val startTime = now - days.toLong() * 24 * 60 * 60 * 1000L
        val summaries = mutableListOf<SleepSummary>()
        val cursor = db.query(
            "XIAOMI_SLEEP_TIME_SAMPLE",
            arrayOf("TIMESTAMP", "WAKEUP_TIME", "TOTAL_DURATION", "DEEP_SLEEP_DURATION",
                "LIGHT_SLEEP_DURATION", "REM_SLEEP_DURATION", "AWAKE_DURATION", "IS_AWAKE"),
            "TIMESTAMP >= ?",
            arrayOf(startTime.toString()),
            null, null, "TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                summaries.add(SleepSummary(
                    timestamp = it.getLong(0),
                    wakeupTime = it.getLong(1),
                    totalDuration = it.getInt(2),
                    deepSleep = it.getInt(3),
                    lightSleep = it.getInt(4),
                    remSleep = it.getInt(5),
                    awakeDuration = it.getInt(6),
                    isAwake = it.getInt(7) == 1,
                ))
            }
        }
        return summaries
    }

    private fun readLatestSpo2AndStressXiaomi(db: SQLiteDatabase): Pair<Int?, Int?> {
        val startSec = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond
        var spo2: Int? = null
        var stress: Int? = null
        val c1 = db.query("XIAOMI_ACTIVITY_SAMPLE", arrayOf("SPO2"), "TIMESTAMP >= ? AND SPO2 IS NOT NULL AND SPO2 > 0", arrayOf(startSec.toString()), null, null, "TIMESTAMP DESC", "1")
        c1.use { if (it.moveToFirst()) spo2 = getIntOrNull(it, 0) }
        val c2 = db.query("XIAOMI_ACTIVITY_SAMPLE", arrayOf("STRESS"), "TIMESTAMP >= ? AND STRESS IS NOT NULL AND STRESS > 0", arrayOf(startSec.toString()), null, null, "TIMESTAMP DESC", "1")
        c2.use { if (it.moveToFirst()) stress = getIntOrNull(it, 0) }
        return Pair(spo2, stress)
    }

    // ==================== 华为实现 ====================
    //
    // 已验证事实（基于 HUAWEI Band 9 / Band 8 两份实测数据库 + Gadgetbridge 官方 GitHub 源码 HuaweiSampleProvider.java）：
    // 1. HUAWEI_ACTIVITY_SAMPLE.TIMESTAMP 单位是【秒】（不是毫秒）
    // 2. 数值字段无数据用字面值 -1（NOT_MEASURED）表示，不是 SQL NULL
    // 3. 每条真实采样会同时写入一条占位/标记行，timestamp 与 otherTimestamp 互相指向对方
    //    判断真实数据行的官方方法：TIMESTAMP <= OTHER_TIMESTAMP（来自 getGBActivitySamplesHighRes）
    // 4. CALORIES 原始整数需 /1000 才是 kcal（对应官方 getActiveCalories()，即运动活跃消耗，不含基础代谢）
    // 5. DISTANCE 单位是【米】（官方 getDistanceCm() = getDistance() * 100）
    // 6. HEART_RATE / RESTING_HEART_RATE / SPO2 有效性用 > 0
    // 7. STEPS / CALORIES / DISTANCE 有效性用 != -1（0 是合法值）
    //
    // 注意时间单位差异（各表不同，必须分别换算比较范围）：
    // - HUAWEI_ACTIVITY_SAMPLE    TIMESTAMP 单位 = 秒
    // - HUAWEI_STRESS_SAMPLE      TIMESTAMP 单位 = 毫秒
    // - HUAWEI_SLEEP_STATS_SAMPLE TIMESTAMP 单位 = 毫秒（已用真实数据验证）

    /**
     * 华为：读取最近 [days] 天的每日汇总。
     *
     * 华为没有现成的每日汇总表，对 HUAWEI_ACTIVITY_SAMPLE 按本地日期逐天聚合。
     * 压力数据来自 HUAWEI_STRESS_SAMPLE（注意该表时间戳为毫秒，需单独换算范围）。
     */
    private fun readDailySummariesHuawei(db: SQLiteDatabase, days: Int): List<DailySummary> {
        val summaries = mutableListOf<DailySummary>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        for (i in 0 until days) {
            try {
                val date = today.minusDays(i.toLong())
                // 活动表时间戳单位是秒
                val dayStartSec = date.atStartOfDay(zone).toInstant().epochSecond
                val dayEndSec = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
                // 压力表时间戳单位是毫秒
                val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                // 占位行过滤条件：TIMESTAMP <= OTHER_TIMESTAMP（仅活动表需要）
                val realRowFilter = "TIMESTAMP <= OTHER_TIMESTAMP"

                // 步数：SUM(STEPS)，过滤 STEPS != -1
                val steps = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("SUM(STEPS)"),
                        "$realRowFilter AND STEPS != -1 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else 0 }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "华为 daily 步数查询失败 date=$date", e); 0
                }

                // 卡路里：SUM(CALORIES)，过滤 CALORIES != -1；原始整数 /1000 转 kcal
                // （CALORIES 对应官方 getActiveCalories()，即运动活跃消耗，不含基础代谢）
                val calories = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("SUM(CALORIES)"),
                        "$realRowFilter AND CALORIES != -1 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) (c.getInt(0) / 1000) else null
                    }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "华为 daily 卡路里查询失败 date=$date", e); null
                }

                // 心率：MAX/MIN/AVG(HEART_RATE)，过滤 HEART_RATE > 0
                val (hrMax, hrMin, hrAvg) = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("MAX(HEART_RATE)", "MIN(HEART_RATE)", "AVG(HEART_RATE)"),
                        "$realRowFilter AND HEART_RATE > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c ->
                        if (c.moveToFirst()) Triple(
                            if (c.isNull(0)) null else c.getInt(0),
                            if (c.isNull(1)) null else c.getInt(1),
                            if (c.isNull(2)) null else c.getDouble(2).toInt()
                        ) else Triple(null, null, null)
                    }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "华为 daily 心率查询失败 date=$date", e)
                    Triple(null, null, null)
                }

                // 静息心率：当天 RESTING_HEART_RATE > 0 的最新一条（取真实测量值，比 AVG 更贴近设备读数）
                val hrResting = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("RESTING_HEART_RATE"),
                        "$realRowFilter AND RESTING_HEART_RATE > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, "TIMESTAMP DESC", "1"
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else null }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "华为 daily 静息心率查询失败 date=$date", e); null
                }

                // 血氧日均：AVG(SPO)，过滤 SPO > 0（华为活动表血氧列名是 SPO，不是 SPO2）
                val spo2Avg = try {
                    db.query(
                        "HUAWEI_ACTIVITY_SAMPLE",
                        arrayOf("AVG(SPO)"),
                        "$realRowFilter AND SPO > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartSec.toString(), dayEndSec.toString()),
                        null, null, null
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0).toInt() else null }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "华为 daily 血氧查询失败 date=$date", e); null
                }

                // 压力日均：AVG(STRESS)，来自 HUAWEI_STRESS_SAMPLE，时间戳为毫秒
                val stressAvg = try {
                    db.query(
                        "HUAWEI_STRESS_SAMPLE",
                        arrayOf("AVG(STRESS)"),
                        "STRESS > 0 AND TIMESTAMP >= ? AND TIMESTAMP < ?",
                        arrayOf(dayStartMs.toString(), dayEndMs.toString()),
                        null, null, null
                    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0).toInt() else null }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "华为 daily 压力查询失败 date=$date", e); null
                }

                val timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
                summaries.add(
                    DailySummary(
                        timestamp = timestamp,
                        date = date,
                        steps = steps,
                        hrResting = hrResting,
                        hrMax = hrMax,
                        hrMin = hrMin,
                        hrAvg = hrAvg,
                        stressAvg = stressAvg,
                        calories = calories,
                        spo2Avg = spo2Avg,
                    )
                )
            } catch (e: Exception) {
                DebugLog.e(TAG, "华为 daily 汇总构建失败", e)
            }
        }
        return summaries.sortedBy { it.timestamp }
    }

    private fun readLatestActivitySampleHuawei(db: SQLiteDatabase): ActivitySample? {
        val realRowFilter = "TIMESTAMP <= OTHER_TIMESTAMP"
        val cursor = db.query(
            "HUAWEI_ACTIVITY_SAMPLE",
            arrayOf("TIMESTAMP", "HEART_RATE", "STEPS", "SPO", "RAW_INTENSITY"),
            "$realRowFilter AND HEART_RATE > 0",
            null, null, null,
            "TIMESTAMP DESC", "1"
        )
        cursor.use {
            return if (it.moveToFirst()) {
                // HUAWEI_ACTIVITY_SAMPLE 时间戳单位为秒，对外统一转为毫秒
                // 华为活动表没有 STRESS 列，压力只能从 HUAWEI_STRESS_SAMPLE 单独查询
                ActivitySample(
                    timestamp = it.getLong(0) * 1000,
                    heartRate = getIntOrNull(it, 1),
                    steps = getIntOrNull(it, 2),
                    stress = null,
                    spo2 = getIntOrNull(it, 3),
                    rawIntensity = getIntOrNull(it, 4)
                )
            } else null
        }
    }

    private fun readSleepSummariesHuawei(db: SQLiteDatabase, days: Int): List<SleepSummary> {
        val now = System.currentTimeMillis()
        val startTime = now - days.toLong() * 24 * 60 * 60 * 1000L
        val summaries = mutableListOf<SleepSummary>()
        val cursor = db.query(
            "HUAWEI_SLEEP_STATS_SAMPLE",
            arrayOf(
                "TIMESTAMP", "WAKEUP_TIME", "TOTAL_DURATION",
                "DEEP_SLEEP_DURATION", "LIGHT_SLEEP_DURATION",
                "REM_SLEEP_DURATION", "AWAKE_DURATION", "IS_AWAKE"
            ),
            "TIMESTAMP >= ?",
            arrayOf(startTime.toString()),
            null, null, "TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                summaries.add(
                    SleepSummary(
                        timestamp = it.getLong(0),
                        wakeupTime = it.getLong(1),
                        totalDuration = it.getInt(2),
                        deepSleep = it.getInt(3),
                        lightSleep = it.getInt(4),
                        remSleep = it.getInt(5),
                        awakeDuration = it.getInt(6),
                        isAwake = it.getInt(7) == 1,
                    )
                )
            }
        }
        return summaries
    }

    private fun readLatestSpo2AndStressHuawei(db: SQLiteDatabase): Pair<Int?, Int?> {
        val realRowFilter = "TIMESTAMP <= OTHER_TIMESTAMP"
        var spo2: Int? = null
        var stress: Int? = null

        // SPO 从活动表取（时间戳单位为秒；华为活动表血氧列名是 SPO，不是 SPO2）。
        // 单独包 try/catch：这条查询失败不能连累下面的压力查询。
        try {
            val c1 = db.query(
                "HUAWEI_ACTIVITY_SAMPLE",
                arrayOf("SPO"),
                "$realRowFilter AND SPO > 0",
                null, null, null,
                "TIMESTAMP DESC", "1"
            )
            c1.use { if (it.moveToFirst()) spo2 = getIntOrNull(it, 0) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "华为 latest 血氧查询失败", e)
        }

        // STRESS 从独立压力表取（时间戳单位为毫秒）。同样单独包 try/catch。
        try {
            val c2 = db.query(
                "HUAWEI_STRESS_SAMPLE",
                arrayOf("STRESS"),
                "STRESS > 0",
                null, null, null,
                "TIMESTAMP DESC", "1"
            )
            c2.use { if (it.moveToFirst()) stress = getIntOrNull(it, 0) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "华为 latest 压力查询失败", e)
        }

        return Pair(spo2, stress)
    }

    private fun getIntOrNull(cursor: android.database.Cursor, index: Int): Int? {
        return if (cursor.isNull(index)) null else cursor.getInt(index)
    }
}
