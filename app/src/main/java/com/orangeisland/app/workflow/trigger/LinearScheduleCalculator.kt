package com.orangeisland.app.workflow.trigger

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Cron subset used by the linear [TimeTriggerFamily]. A small, deliberately restricted dialect
 * (daily-at, every-N-hours, every-N-minutes) — the same shapes [ScheduleCalculator] already
 * recognises for graph-mode schedules. Kept separate from [ScheduleCalculator] so the linear
 * path doesn't depend on [com.orangeisland.app.model.TriggerSpec] plumbing, and so the next-fire
 * math can be unit-tested in isolation.
 *
 * Recognised 5-field cron shapes (asterisk-N is the "every N units" cron syntax):
 *   "M H . . ."    — daily at H:M (dots are the day/month/weekday wildcards)
 *   "0 star-N . ." — every N hours
 *   "star-N . . ." — every N minutes (clamped to the 15-minute WorkManager floor)
 *
 * Independent implementation.
 */
object LinearScheduleCalculator {

    /** WorkManager's minimum periodic interval. Requests below this are clamped up to it. */
    const val MIN_PERIOD_MINUTES = 15L

    /** The repeating period (ms) a [TimeTriggerFamily] PeriodicWorkRequest should use, or null if
     *  the cron form isn't recognised (caller should fall back to one-shot re-enqueue). */
    fun cronPeriodMs(expr: String): Long? {
        val (minute, hour) = splitOrNull(expr) ?: return null
        return when {
            // "0 */N * * *" — every N hours.
            minute == "0" && hour.startsWith("*/") -> {
                val n = hour.substring(2).toLongOrNull() ?: return null
                TimeUnit.HOURS.toMillis(n.coerceAtLeast(1))
            }
            // "*/N * * * *" — every N minutes (clamped to 15).
            minute.startsWith("*/") && hour == "*" -> {
                val n = minute.substring(2).toLongOrNull() ?: return null
                TimeUnit.MINUTES.toMillis(n.coerceAtLeast(MIN_PERIOD_MINUTES))
            }
            // "M H * * *" — daily, expressed as a 24h periodic interval with an initial delay.
            minute.matches(Regex("\\d+")) && hour.matches(Regex("\\d+")) -> TimeUnit.DAYS.toMillis(1)
            else -> null
        }
    }

    /** Epoch-ms of the next fire of [expr] after [now]. Null if [expr] isn't a recognised shape. */
    fun cronNextMs(expr: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val (minute, hour) = splitOrNull(expr) ?: return null
        val znow = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        return when {
            // "0 */N * * *" — top of the next Nth hour.
            minute == "0" && hour.startsWith("*/") -> {
                val n = hour.substring(2).toIntOrNull() ?: return null
                if (n < 1) return null
                var candidate = znow.truncatedTo(ChronoUnit.HOURS).plusHours(n.toLong())
                if (!candidate.isAfter(znow)) candidate = candidate.plusHours(n.toLong())
                candidate.toInstant().toEpochMilli()
            }
            // "*/N * * *" — next N-minute boundary, clamped to 15.
            minute.startsWith("*/") && hour == "*" -> {
                val n = minute.substring(2).toIntOrNull() ?: return null
                if (n < 1) return null
                val step = n.coerceAtLeast(MIN_PERIOD_MINUTES.toInt()).toLong()
                var candidate = znow.truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes(((znow.minute / step) + 1) * step)
                if (!candidate.isAfter(znow)) candidate = candidate.plusMinutes(step)
                candidate.toInstant().toEpochMilli()
            }
            // "M H * * *" — next H:M, tomorrow if it has passed.
            minute.matches(Regex("\\d+")) && hour.matches(Regex("\\d+")) -> {
                val m = minute.toInt(); val h = hour.toInt()
                if (m !in 0..59 || h !in 0..23) return null
                var candidate = znow.toLocalDate().atTime(h, m).atZone(zone)
                if (!candidate.isAfter(znow)) candidate = candidate.plusDays(1)
                candidate.toInstant().toEpochMilli()
            }
            else -> null
        }
    }

    private fun splitOrNull(expr: String): Pair<String, String>? {
        val parts = expr.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null
        return parts[0] to parts[1]
    }
}
