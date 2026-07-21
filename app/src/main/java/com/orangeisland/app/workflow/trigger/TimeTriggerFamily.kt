package com.orangeisland.app.workflow.trigger

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Trigger family for [LinearTrigger.TimeCron]. Reuses WorkManager (same backend the graph-mode
 * [com.orangeisland.app.workflow.WorkflowWorker] uses) with workflow-scoped unique-work names so
 * one workflow's schedule never collides with another's, and so [sync]'s diff can cancel exactly
 * the disabled workflow's worker.
 *
 * Two supported shapes:
 *  - time_of_day (HH:mm) + optional days_of_week (ISO 1=Mon..7=Sun): a 24h PeriodicWorkRequest
 *    with an initial delay to the next fire. The day-of-week filter is enforced in the worker
 *    (the periodic schedule fires daily; the worker skips non-matching days) because WorkManager
 *    has no native "weekly" cadence.
 *  - cron (5-field subset recognised by [LinearScheduleCalculator]): falls back to the same
 *    periodic / one-shot logic [com.orangeisland.app.workflow.ScheduleCalculator] already
 *    implements for graph-mode schedules.
 *
 * Independent implementation.
 */
class TimeTriggerFamily(
    private val context: Context,
    private val scope: CoroutineScope
) : TriggerFamily {

    override val name: String = "time_cron"

    @Volatile private var lastSnapshot: List<LinearWorkflow> = emptyList()
    @Volatile private var fireCallback: TriggerFireCallback? = null

    override fun handles(trigger: LinearTrigger): Boolean = trigger is LinearTrigger.TimeCron

    override suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback) {
        fireCallback = callback
        val previous = lastSnapshot.associateBy { it.id }
        val current = matching.associateBy { it.id }
        // Cancel workers for workflows that fell out of the matching set (disabled, deleted, or
        // switched trigger kind).
        for (id in previous.keys - current.keys) cancelWork(id)
        // Schedule added or changed workflows. A re-sync with the same signature is a no-op.
        for ((id, wf) in current) {
            val prev = previous[id]
            if (prev == null || prev.updatedAt != wf.updatedAt) scheduleWork(wf)
        }
        lastSnapshot = matching
    }

    override suspend fun shutdown() {
        for (wf in lastSnapshot) cancelWork(wf.id)
        lastSnapshot = emptyList()
        fireCallback = null
    }

    private fun cancelWork(workflowId: String) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(workName(workflowId)) }
            .onFailure { DebugLog.w(TAG, "cancel work failed for $workflowId", it) }
    }

    private fun scheduleWork(wf: LinearWorkflow) {
        val spec = wf.trigger as? LinearTrigger.TimeCron ?: return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        // time_of_day path: 24h period, initial delay to next matching day.
        if (!spec.timeOfDay.isNullOrBlank()) {
            val periodMs = 24L * 60 * 60 * 1000
            val nextMs = nextTimeOfDayFireMs(spec, zone, now) ?: return
            val delay = (nextMs - now).coerceAtLeast(0L)
            val req = PeriodicWorkRequestBuilder<LinearTimeWorker>(periodMs, TimeUnit.MILLISECONDS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_WORKFLOW_ID to wf.id))
                .build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(workName(wf.id), ExistingPeriodicWorkPolicy.REPLACE, req)
            }.onFailure { DebugLog.w(TAG, "periodic enqueue failed for ${wf.id}", it) }
            return
        }

        // cron path: reuse the shared calculator. Periodic forms become PeriodicWorkRequest;
        // OneShot forms become OneTimeWorkRequest that re-enqueues itself via the worker.
        spec.cron?.let { cron ->
            val periodMs = LinearScheduleCalculator.cronPeriodMs(cron)
            if (periodMs != null) {
                val nextMs = LinearScheduleCalculator.cronNextMs(cron, now) ?: return
                val delay = (nextMs - now).coerceAtLeast(0L)
                val req = PeriodicWorkRequestBuilder<LinearTimeWorker>(periodMs, TimeUnit.MILLISECONDS)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_WORKFLOW_ID to wf.id))
                    .build()
                runCatching {
                    WorkManager.getInstance(context)
                        .enqueueUniquePeriodicWork(workName(wf.id), ExistingPeriodicWorkPolicy.REPLACE, req)
                }.onFailure { DebugLog.w(TAG, "periodic cron enqueue failed for ${wf.id}", it) }
                return
            }
            val nextMs = LinearScheduleCalculator.cronNextMs(cron, now) ?: return
            val delay = (nextMs - now).coerceAtLeast(60_000L)
            val req = OneTimeWorkRequestBuilder<LinearTimeWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_WORKFLOW_ID to wf.id))
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(workName(wf.id), ExistingWorkPolicy.REPLACE, req)
            }.onFailure { DebugLog.w(TAG, "one-shot cron enqueue failed for ${wf.id}", it) }
        }
    }

    /** Called by [LinearTimeWorker.doWork] after a schedule fires. Routes through the registry's
     *  callback when present; otherwise reads from the repository (cold-start / process-death
     *  recovery) and fires directly. Returns true if the workflow fired (or was correctly skipped
     *  due to the day-of-week filter), false if it should not be re-enqueued. */
    suspend fun onWorkerFired(workflowId: String): Boolean {
        val cb = fireCallback
        val wf = lastSnapshot.firstOrNull { it.id == workflowId }
            ?: return false   // not in our snapshot; worker will be cancelled on next sync
        val spec = wf.trigger as? LinearTrigger.TimeCron ?: return false

        // day-of-week gate (time_of_day path runs daily; the worker skips non-matching days).
        if (!spec.timeOfDay.isNullOrBlank() && spec.daysOfWeek.isNotEmpty()) {
            val today = ZonedDateTime.now(ZoneId.systemDefault()).dayOfWeek
            val allowed = spec.daysOfWeek.mapNotNull { isoDayOfWeek(it) }.toSet()
            if (today !in allowed) {
                DebugLog.d(TAG, "$workflowId skipped: $today not in days_of_week")
                return true
            }
        }

        if (cb != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { cb.onFire(wf.id, wf.trigger) }
                    .onFailure { DebugLog.w(TAG, "fire callback failed for $workflowId", it) }
            }
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "TimeTriggerFamily"
        const val KEY_WORKFLOW_ID = "workflow_id"
        fun workName(workflowId: String) = "wf_timecron_$workflowId"

        /** Publishes the live family so [LinearTimeWorker] (which runs in a fresh process) can
         *  reach it. Bound by [com.orangeisland.app.di.AppContainer] once the registry is wired. */
        @Volatile private var instance: TimeTriggerFamily? = null
        fun bind(family: TimeTriggerFamily) { instance = family }
        fun get(): TimeTriggerFamily? = instance

        /** Compute the next epoch-ms fire for a time_of_day + days_of_week spec. */
        private fun nextTimeOfDayFireMs(
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
                val allowed = spec.daysOfWeek.mapNotNull { isoDayOfWeek(it) }.toSet()
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
}
