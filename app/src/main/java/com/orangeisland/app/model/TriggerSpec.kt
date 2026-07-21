package com.orangeisland.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What starts a workflow run. Carried by [StartNode].
 *
 * Lives in the model layer (not workflow/) because [StartNode] holds one as a field and the whole
 * [Workflow] graph — including triggers — round-trips through kotlinx.serialization into the
 * workflows table's graphJson blob. Keeping it here preserves a clean dependency direction
 * (workflow engine → model, never the reverse).
 *
 * Independent implementation. The concrete trigger kinds and their configs are Orange Island's
 * own; the trigger layer (WorkflowWorker / WorkflowIntentReceiver / AiToolProvider / app-open
 * hook) wires each kind up.
 *
 *  - [Manual]      : the user taps "Run" in the editor.
 *  - [Schedule]    : a time-based firing (interval / one-shot / cron-like).
 *  - [IntentAction]: an external app broadcasts an Intent with a matching action.
 *  - [AppOpen]     : the workflow runs once when Orange Island is launched (cold start).
 *  - [Voice]       : a spoken phrase launches the run (voice-trigger feature).
 *  - [Api]         : launched by the AI via the workflow_* tool family.
 */
@Serializable
sealed class TriggerSpec {
    @Serializable
    @SerialName("manual")
    data object Manual : TriggerSpec()

    @Serializable
    @SerialName("schedule")
    data class Schedule(
        val mode: ScheduleMode,
        /** Mode-specific config. Interval → {intervalMs}; OneShot → {atMs}; CronLike → {expr}. */
        val config: Map<String, String>
    ) : TriggerSpec()

    @Serializable
    @SerialName("intent")
    data class IntentAction(val action: String) : TriggerSpec()

    @Serializable
    @SerialName("app_open")
    data object AppOpen : TriggerSpec()

    @Serializable
    @SerialName("voice")
    data class Voice(val keyword: String? = null) : TriggerSpec()

    @Serializable
    @SerialName("api")
    data object Api : TriggerSpec()
}

@Serializable
sealed class ScheduleMode {
    @Serializable
    @SerialName("interval")
    data object Interval : ScheduleMode()

    @Serializable
    @SerialName("oneshot")
    data object OneShot : ScheduleMode()

    /** Simplified cron — supports a small subset (daily-at, every-N-hours, every-N-minutes),
     *  computed by the scheduler into concrete next-fire times. Not a full cron engine. */
    @Serializable
    @SerialName("cronlike")
    data object CronLike : ScheduleMode()
}
