package com.orangeisland.app.workflow.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import com.orangeisland.app.data.repository.WorkflowRepository
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Signal source for every broadcast-based trigger (WiFi, power, headphones, screen, battery,
 * Bluetooth). Each kind has its **own BroadcastReceiver subclass** that owns its filter, its
 * snapshot of matching workflows, and its register/unregister lifecycle — there is no shared
 * abstract base class driving a common register/diff loop.
 *
 * Each receiver subscribes to [WorkflowRepository.observeEnabledLinear] and only registers itself
 * with the system when its matching set is non-empty. When the set goes empty it unregisters, so a
 * signal nobody listens for costs nothing.
 *
 * Independent implementation.
 */
object BroadcastSignalSource {

    fun start(
        context: Context,
        scope: CoroutineScope,
        repository: WorkflowRepository,
        starter: WorkflowStarter
    ): Job = scope.launch(Dispatchers.IO) {
        // One subscription drives every receiver: each receiver lazily constructs itself on first
        // use and then re-derives its matching set from the same enabled list on every emission.
        repository.observeEnabledLinear().collectLatest { all ->
            WifiReceiver.update(all, context, scope, starter)
            PowerReceiver.update(all, context, scope, starter)
            HeadphonesReceiver.update(all, context, scope, starter)
            ScreenReceiver.update(all, context, scope, starter)
            BatteryReceiver.update(all, context, scope, starter)
            BluetoothReceiver.update(all, context, scope, starter)
        }
    }

    /** Toggle this receiver's registration on/off and refresh its snapshot. */
    private abstract class ManagedReceiver(
        protected val context: Context,
        protected val scope: CoroutineScope,
        protected val starter: WorkflowStarter
    ) : BroadcastReceiver() {
        @Volatile private var registered = false
        @Volatile var matching: List<LinearWorkflow> = emptyList()
            protected set

        abstract fun intentFilter(): IntentFilter
        /** Return the ids+triggers that match this broadcast (empty if none). */
        abstract fun selects(workflows: List<LinearWorkflow>): List<LinearWorkflow>
        /** True if this receiver should be live given [matching] (typically `matching.isNotEmpty()`). */
        open fun shouldLive() = matching.isNotEmpty()

        fun reconcile(all: List<LinearWorkflow>) {
            matching = selects(all)
            if (shouldLive()) registerOnce() else unregisterOnce()
        }

        private fun registerOnce() {
            if (registered) return
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Context.RECEIVER_NOT_EXPORTED else 0
            runCatching { context.registerReceiver(this, intentFilter(), flags) }
                .onFailure { DebugLog.e("ManagedReceiver", "register failed", it) }
                .onSuccess { registered = true }
        }

        private fun unregisterOnce() {
            if (!registered) return
            runCatching { context.unregisterReceiver(this) }
            registered = false
        }

        /** Fire every workflow whose trigger passes [pred], on the IO dispatcher. */
        protected fun fireWhere(pred: (LinearTrigger) -> Boolean) {
            val toFire = matching.filter { pred(it.trigger) }
            if (toFire.isEmpty()) return
            scope.launch { toFire.forEach { runCatching { starter.start(it.id) } } }
        }

        /** Tear down on shutdown. */
        fun teardown() = unregisterOnce()
    }

    // ── WiFi ─────────────────────────────────────────────────────────────────
    private class WifiReceiver(
        context: Context, scope: CoroutineScope, starter: WorkflowStarter
    ) : ManagedReceiver(context, scope, starter) {
        override fun intentFilter() = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        override fun selects(workflows: List<LinearWorkflow>) =
            workflows.filter {
                it.trigger is LinearTrigger.WifiConnected ||
                    it.trigger is LinearTrigger.WifiDisconnected
            }

        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return
            @Suppress("DEPRECATION")
            val info = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
            val nowConnected = info?.isConnected == true
            fireWhere { t ->
                (t is LinearTrigger.WifiConnected && nowConnected) ||
                    (t is LinearTrigger.WifiDisconnected && !nowConnected)
            }
        }

        companion object {
            private var instance: WifiReceiver? = null
            fun update(all: List<LinearWorkflow>, ctx: Context, scope: CoroutineScope, starter: WorkflowStarter) {
                val r = instance ?: WifiReceiver(ctx, scope, starter).also { instance = it }
                r.reconcile(all)
            }
        }
    }

    // ── Power ────────────────────────────────────────────────────────────────
    private class PowerReceiver(
        context: Context, scope: CoroutineScope, starter: WorkflowStarter
    ) : ManagedReceiver(context, scope, starter) {
        override fun intentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        override fun selects(workflows: List<LinearWorkflow>) =
            workflows.filter {
                it.trigger is LinearTrigger.PowerConnected ||
                    it.trigger is LinearTrigger.PowerDisconnected
            }
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> fireWhere { it is LinearTrigger.PowerConnected }
                Intent.ACTION_POWER_DISCONNECTED -> fireWhere { it is LinearTrigger.PowerDisconnected }
            }
        }
        companion object {
            private var instance: PowerReceiver? = null
            fun update(all: List<LinearWorkflow>, ctx: Context, scope: CoroutineScope, starter: WorkflowStarter) {
                val r = instance ?: PowerReceiver(ctx, scope, starter).also { instance = it }
                r.reconcile(all)
            }
        }
    }

    // ── Headphones ───────────────────────────────────────────────────────────
    private class HeadphonesReceiver(
        context: Context, scope: CoroutineScope, starter: WorkflowStarter
    ) : ManagedReceiver(context, scope, starter) {
        override fun intentFilter() = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        override fun selects(workflows: List<LinearWorkflow>) =
            workflows.filter {
                it.trigger is LinearTrigger.HeadphonesPlugged ||
                    it.trigger is LinearTrigger.HeadphonesUnplugged
            }
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_HEADSET_PLUG) return
            val state = intent.getIntExtra("state", -1)
            when (state) {
                1 -> fireWhere { it is LinearTrigger.HeadphonesPlugged }
                0 -> fireWhere { it is LinearTrigger.HeadphonesUnplugged }
            }
        }
        companion object {
            private var instance: HeadphonesReceiver? = null
            fun update(all: List<LinearWorkflow>, ctx: Context, scope: CoroutineScope, starter: WorkflowStarter) {
                val r = instance ?: HeadphonesReceiver(ctx, scope, starter).also { instance = it }
                r.reconcile(all)
            }
        }
    }

    // ── Screen ───────────────────────────────────────────────────────────────
    private class ScreenReceiver(
        context: Context, scope: CoroutineScope, starter: WorkflowStarter
    ) : ManagedReceiver(context, scope, starter) {
        override fun intentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        override fun selects(workflows: List<LinearWorkflow>) =
            workflows.filter {
                it.trigger is LinearTrigger.ScreenOn || it.trigger is LinearTrigger.ScreenOff
            }
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> fireWhere { it is LinearTrigger.ScreenOn }
                Intent.ACTION_SCREEN_OFF -> fireWhere { it is LinearTrigger.ScreenOff }
            }
        }
        companion object {
            private var instance: ScreenReceiver? = null
            fun update(all: List<LinearWorkflow>, ctx: Context, scope: CoroutineScope, starter: WorkflowStarter) {
                val r = instance ?: ScreenReceiver(ctx, scope, starter).also { instance = it }
                r.reconcile(all)
            }
        }
    }

    // ── Battery (threshold crossing) ─────────────────────────────────────────
    private class BatteryReceiver(
        context: Context, scope: CoroutineScope, starter: WorkflowStarter
    ) : ManagedReceiver(context, scope, starter) {
        override fun intentFilter() = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        override fun selects(workflows: List<LinearWorkflow>) =
            workflows.filter {
                it.trigger is LinearTrigger.BatteryBelow || it.trigger is LinearTrigger.BatteryAbove
            }
        private var lastPct = -1

        override fun onReceive(ctx: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            if (lastPct < 0) { lastPct = pct; return }   // seed baseline, no fire on first tick
            // Fire each workflow whose threshold was crossed in the right direction this tick.
            val crossed = matching.filter { wf ->
                when (val t = wf.trigger) {
                    is LinearTrigger.BatteryBelow -> lastPct >= t.threshold && pct < t.threshold
                    is LinearTrigger.BatteryAbove -> lastPct <= t.threshold && pct > t.threshold
                    else -> false
                }
            }
            lastPct = pct
            if (crossed.isEmpty()) return
            scope.launch { crossed.forEach { runCatching { starter.start(it.id) } } }
        }

        companion object {
            private var instance: BatteryReceiver? = null
            fun update(all: List<LinearWorkflow>, ctx: Context, scope: CoroutineScope, starter: WorkflowStarter) {
                val r = instance ?: BatteryReceiver(ctx, scope, starter).also { instance = it }
                r.reconcile(all)
            }
        }
    }

    // ── Bluetooth ────────────────────────────────────────────────────────────
    private class BluetoothReceiver(
        context: Context, scope: CoroutineScope, starter: WorkflowStarter
    ) : ManagedReceiver(context, scope, starter) {
        override fun intentFilter() = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        override fun selects(workflows: List<LinearWorkflow>) =
            workflows.filter {
                it.trigger is LinearTrigger.BluetoothConnected ||
                    it.trigger is LinearTrigger.BluetoothDisconnected
            }
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val dev = intent?.getParcelableExtra<android.bluetooth.BluetoothDevice>(
                android.bluetooth.BluetoothDevice.EXTRA_DEVICE
            )
            val addr = dev?.address
            when (intent?.action) {
                android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED ->
                    fireWhere { it is LinearTrigger.BluetoothConnected && (it.deviceAddress == null || it.deviceAddress == addr) }
                android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    fireWhere { it is LinearTrigger.BluetoothDisconnected && (it.deviceAddress == null || it.deviceAddress == addr) }
            }
        }
        companion object {
            private var instance: BluetoothReceiver? = null
            fun update(all: List<LinearWorkflow>, ctx: Context, scope: CoroutineScope, starter: WorkflowStarter) {
                val r = instance ?: BluetoothReceiver(ctx, scope, starter).also { instance = it }
                r.reconcile(all)
            }
        }
    }
}
