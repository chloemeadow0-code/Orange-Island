package com.orangeisland.app.workflow.trigger

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Signal source for [LinearTrigger.TimeCron]. Reconciles WorkManager requests against the current
 * matching set: each schedule-keyed workflow gets its own unique-work-name so add/remove is
 * workflow-scoped and never touches another schedule.
 *
 * Two scheduling shapes (delegated to [LinearScheduleCalculator] for the math):
 *  - `time_of_day` (HH:mm) + optional `days_of_week`: a 24h PeriodicWorkRequest with an initial
 *    delay to the next fire. The day filter is enforced in [LinearTimeWorker] (a periodic request
 *    fires daily; the worker skips non-matching days) because WorkManager has no weekly cadence.
 *  - `cron` subset: periodic for the recognised "every N" / daily shapes, one-shot with self-
 *    re-enqueue otherwise.
 *
 * Independent implementation.
 */
object TimeSignalSource {

    fun start(
        context: Context,
        scope: CoroutineScope,
        repository: WorkflowRepository
    ): Job = scope.launch(Dispatchers.IO) {
        repository.observeEnabledLinear().collectLatest { all ->
            val cronWorkflows = all.filter { it.trigger is LinearTrigger.TimeCron }
            reconcileSchedules(context, cronWorkflows)
        }
    }

    private fun reconcileSchedules(context: Context, workflows: List<LinearWorkflow>) {
        val wm = WorkManager.getInstance(context)
        val liveIds = workflows.map { it.id }.toSet()
        // Cancel schedules for workflows that fell out of the set (disabled / deleted / switched).
        // We can't enumerate WorkManager's unique-work names, so we track the last-seen set in a
        // companion and cancel the difference. (See [previousSnapshot].)
        for (id in previousSnapshot - liveIds) {
            runCatching { wm.cancelUniqueWork(workName(id)) }
        }
        // (Re-)schedule each workflow. UPDATE policy makes a re-sync cheap when nothing changed.
        workflows.forEach { wf -> scheduleOne(context, wf) }
        previousSnapshot = liveIds
    }

    private fun scheduleOne(context: Context, wf: LinearWorkflow) {
        val spec = wf.trigger as? LinearTrigger.TimeCron ?: return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val wm = WorkManager.getInstance(context)
        val name = workName(wf.id)
        val data = workDataOf(LinearTimeWorker.KEY_WORKFLOW_ID to wf.id)

        if (!spec.timeOfDay.isNullOrBlank()) {
            val nextMs = nextTimeOfDayMs(spec, zone, now) ?: return
            val delay = (nextMs - now).coerceAtLeast(0L)
            val req = PeriodicWorkRequestBuilder<LinearTimeWorker>(24L, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            runCatching {
                wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, req)
            }.onFailure { DebugLog.w("TimeSignalSource", "periodic tod enqueue failed for ${wf.id}", it) }
            return
        }

        val cron = spec.cron ?: return
        val periodMs = LinearScheduleCalculator.cronPeriodMs(cron)
        if (periodMs != null) {
            val nextMs = LinearScheduleCalculator.cronNextMs(cron, now) ?: return
            val delay = (nextMs - now).coerceAtLeast(0L)
            val req = PeriodicWorkRequestBuilder<LinearTimeWorker>(periodMs, TimeUnit.MILLISECONDS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            runCatching {
                wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, req)
            }.onFailure { DebugLog.w("TimeSignalSource", "periodic cron enqueue failed for ${wf.id}", it) }
            return
        }
        val nextMs = LinearScheduleCalculator.cronNextMs(cron, now) ?: return
        val delay = (nextMs - now).coerceAtLeast(60_000L)
        val req = OneTimeWorkRequestBuilder<LinearTimeWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        runCatching {
            wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
        }.onFailure { DebugLog.w("TimeSignalSource", "one-shot cron enqueue failed for ${wf.id}", it) }
    }

    fun workName(workflowId: String) = "wf_timecron_$workflowId"

    /** Last set of ids we scheduled. Held in the companion so the diff across emissions works. */
    @Volatile private var previousSnapshot: Set<String> = emptySet()

    private fun nextTimeOfDayMs(
        spec: LinearTrigger.TimeCron,
        zone: ZoneId,
        nowMs: Long
    ): Long? {
        val tod = spec.timeOfDay ?: return null
        val parts = tod.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
        var candidate = now.toLocalDate().atTime(LocalTime.of(h, m)).atZone(zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (spec.daysOfWeek.isNotEmpty()) {
            val allowed = spec.daysOfWeek.mapNotNull(::isoDayOfWeek).toSet()
            var hops = 0
            while (candidate.dayOfWeek !in allowed && hops < 8) {
                candidate = candidate.plusDays(1); hops++
            }
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun isoDayOfWeek(iso: Int): DayOfWeek? = when (iso) {
        1 -> DayOfWeek.MONDAY
        2 -> DayOfWeek.TUESDAY
        3 -> DayOfWeek.WEDNESDAY
        4 -> DayOfWeek.THURSDAY
        5 -> DayOfWeek.FRIDAY
        6 -> DayOfWeek.SATURDAY
        7 -> DayOfWeek.SUNDAY
        else -> null
    }
}
