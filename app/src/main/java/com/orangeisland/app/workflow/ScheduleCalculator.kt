package com.orangeisland.app.workflow

import com.orangeisland.app.model.ScheduleMode
import com.orangeisland.app.model.TriggerSpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure functions that compute when a scheduled [TriggerSpec.Schedule] should next fire, and the
 * interval a periodic WorkManager request should use.
 *
 * The "cron-like" mode is deliberately a small subset — daily-at-a-time, every-N-hours,
 * every-N-minutes — rather than a full cron engine. That covers the common automation cases
 * (run every morning, every 6 hours, every 15 minutes) without the parsing complexity of real
 * cron, and keeps WorkManager's 15-minute floor meaningful.
 *
 * Independent implementation.
 */
object ScheduleCalculator {

    /** WorkManager's minimum periodic interval. Requests below this are clamped up to it. */
    const val MIN_PERIOD_MINUTES = 15L

    /**
     * Compute the delay (ms from now) until [trigger]'s next fire. Returns null if the trigger is
     * not a schedule (caller should not have asked) or the config is unparseable.
     */
    fun nextDelayMs(trigger: TriggerSpec.Schedule, now: Long = System.currentTimeMillis()): Long? {
        return when (val mode = trigger.mode) {
            is ScheduleMode.Interval -> {
                val ms = trigger.config["intervalMs"]?.toLongOrNull() ?: return null
                ms.coerceAtLeast(MIN_PERIOD_MINUTES * 60_000L)
            }
            is ScheduleMode.OneShot -> {
                val at = trigger.config["atMs"]?.toLongOrNull()
                    ?: trigger.config["at"]?.let(::parseDateTime)
                    ?: return null
                (at - now).coerceAtLeast(0L)
            }
            is ScheduleMode.CronLike -> {
                val expr = trigger.config["expr"] ?: return null
                nextCronDelayMs(expr, now)
            }
        }
    }

    /**
     * For periodic scheduling: the repeating interval WorkManager should use. Only meaningful for
     * Interval and repeating CronLike; OneShot returns null (use a one-time request instead).
     */
    fun periodicIntervalMs(trigger: TriggerSpec.Schedule): Long? {
        return when (trigger.mode) {
            is ScheduleMode.Interval -> {
                val ms = trigger.config["intervalMs"]?.toLongOrNull() ?: return null
                ms.coerceAtLeast(MIN_PERIOD_MINUTES * 60_000L)
            }
            is ScheduleMode.CronLike -> cronIntervalMs(trigger.config["expr"] ?: return null)
            is ScheduleMode.OneShot -> null
        }
    }

    // ── Cron-like subset ────────────────────────────────────────────────────
    // Recognised shapes (5-field cron, only the patterns below evaluate):
    //   "M H * * *"      → daily at H:M        (e.g. "30 2 * * *" = 02:30 daily)
    //   "0 */N * * *"    → every N hours       (N >= 1)
    //   "*/N * * * *"    → every N minutes     (N >= 1, clamped to 15-minute floor)
    // Anything else → null (the workflow is skipped at schedule time with a log entry).

    private fun nextCronDelayMs(expr: String, now: Long): Long? {
        val parts = expr.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null
        val (minute, hour) = parts[0] to parts[1]
        return when {
            // "0 */N * * *" — every N hours.
            minute == "0" && hour.startsWith("*/") -> {
                val n = hour.substring(2).toLongOrNull() ?: return null
                if (n < 1) return null
                nextTopOfHour(n, now)
            }
            // "*/N * * *" — every N minutes (minute field has */N, hour is *).
            minute.startsWith("*/") && hour == "*" -> {
                val n = minute.substring(2).toLongOrNull() ?: return null
                if (n < 1) return null
                nextMinuteMultiple(n, now)
            }
            // "M H * * *" — daily at H:M.
            minute.matches(Regex("\\d+")) && hour.matches(Regex("\\d+")) -> {
                val m = minute.toInt(); val h = hour.toInt()
                if (m !in 0..59 || h !in 0..23) return null
                nextDailyAt(h, m, now)
            }
            else -> null
        }
    }

    private fun cronIntervalMs(expr: String): Long? {
        val parts = expr.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null
        val (minute, hour) = parts[0] to parts[1]
        return when {
            minute == "0" && hour.startsWith("*/") -> {
                val n = hour.substring(2).toLongOrNull() ?: return null
                TimeUnit.HOURS.toMillis(n.coerceAtLeast(1))
            }
            minute.startsWith("*/") && hour == "*" -> {
                val n = minute.substring(2).toLongOrNull() ?: return null
                TimeUnit.MINUTES.toMillis(n.coerceAtLeast(MIN_PERIOD_MINUTES))
            }
            // Daily-at: express as a 24h periodic interval with an initial delay.
            minute.matches(Regex("\\d+")) && hour.matches(Regex("\\d+")) -> TimeUnit.DAYS.toMillis(1)
            else -> null
        }
    }

    private fun nextTopOfHour(everyNHours: Long, now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.HOUR_OF_DAY, everyNHours.toInt())
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis - now
    }

    private fun nextMinuteMultiple(everyNMinutes: Long, now: Long): Long {
        val n = everyNMinutes.coerceAtLeast(MIN_PERIOD_MINUTES)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.MINUTE, n.toInt())
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis - now
    }

    private fun nextDailyAt(hour: Int, minute: Int, now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis - now
    }

    private val datetimeFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm"
    )

    private fun parseDateTime(s: String): Long? {
        for (fmt in datetimeFormats) {
            try {
                return SimpleDateFormat(fmt, Locale.US).parse(s)?.time
            } catch (_: Exception) { /* try next */ }
        }
        return s.toLongOrNull()   // raw epoch ms
    }
}
