package com.orangeisland.app.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class AnniversaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    /** true = 每年在 month/day 重复（在一起纪念日、生日之类）。false = 只算这一次（比如某次旅行的日期），
     *  过了就不再"下一次"，daysUntilNext 会变成负数。 */
    val recurring: Boolean = true
)

object AnniversaryUtils {

    /** 把 day 夹到 year/month 实际的最大天数以内（处理 2 月 29 号这种边界）。 */
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val lastDay = LocalDate.of(year, month, 1).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceIn(1, lastDay))
    }

    /** 下一次触发的具体日期：recurring 的会滚动到下一年；一次性的就是它本身。 */
    fun nextOccurrence(entry: AnniversaryEntry, today: LocalDate = LocalDate.now()): LocalDate {
        return if (entry.recurring) {
            var next = safeDate(today.year, entry.month, entry.day)
            if (next.isBefore(today)) next = safeDate(today.year + 1, entry.month, entry.day)
            next
        } else {
            safeDate(entry.year, entry.month, entry.day)
        }
    }

    /** 距离下一次还有几天。recurring 的恒 >= 0（今天算 0）；一次性的过去了会是负数。 */
    fun daysUntilNext(entry: AnniversaryEntry, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, nextOccurrence(entry, today))

    /** 距今是第几年（只对 recurring 有意义，比如"在一起第 3 年"）。 */
    fun yearsSince(entry: AnniversaryEntry, today: LocalDate = LocalDate.now()): Int =
        nextOccurrence(entry, today).year - entry.year

    fun formatDate(entry: AnniversaryEntry): String = "%d年%d月%d日".format(entry.year, entry.month, entry.day)
}
