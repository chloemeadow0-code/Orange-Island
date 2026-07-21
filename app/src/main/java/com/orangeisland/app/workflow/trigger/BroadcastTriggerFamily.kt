package com.orangeisland.app.workflow.trigger

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import com.orangeisland.app.model.LinearTrigger
import com.orangeisland.app.model.LinearWorkflow
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Trigger family driven by Android [BroadcastReceiver]s. Handles the six broadcast-based trigger
 * kinds: WiFi connect/disconnect, power connect/disconnect, headphones plug/unplug, screen on/off,
 * battery-above/below threshold transitions, and Bluetooth device connect/disconnect.
 *
 * All receivers are registered at runtime (not in the manifest) via [Context.registerReceiver], so
 * a workflow that nobody uses costs zero — the family unregisters its receiver when [sync] gets an
 * empty matching list. The [RECEIVER_NOT_EXPORTED] flag is set on Android 13+ since these are
 * app-internal receivers (system broadcasts are still delivered).
 *
 * Battery transitions track the previous level in memory and only fire on a threshold crossing
 * (e.g. BatteryBelow(20) fires when level drops from ≥20 to <20, not on every battery tick).
 *
 * Independent implementation.
 */
class BroadcastTriggerFamily(
    private val context: Context,
    private val scope: CoroutineScope
) : TriggerFamily {

    override val name: String = "broadcast"

    private var wifiReceiver: BroadcastReceiver? = null
    private var powerReceiver: BroadcastReceiver? = null
    private var headphoneReceiver: BroadcastReceiver? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BatteryReceiver? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

    @Volatile private var matching: List<LinearWorkflow> = emptyList()

    override fun handles(trigger: LinearTrigger): Boolean = when (trigger) {
        is LinearTrigger.WifiConnected, is LinearTrigger.WifiDisconnected,
        is LinearTrigger.PowerConnected, is LinearTrigger.PowerDisconnected,
        is LinearTrigger.HeadphonesPlugged, is LinearTrigger.HeadphonesUnplugged,
        is LinearTrigger.ScreenOn, is LinearTrigger.ScreenOff,
        is LinearTrigger.BatteryBelow, is LinearTrigger.BatteryAbove,
        is LinearTrigger.BluetoothConnected, is LinearTrigger.BluetoothDisconnected -> true
        else -> false
    }

    override suspend fun sync(matching: List<LinearWorkflow>, callback: TriggerFireCallback) {
        this.matching = matching
        // Each receiver is registered only if at least one matching workflow needs it, and torn
        // down otherwise. This keeps the listener surface proportional to actual usage.
        syncWifi(matching, callback)
        syncPower(matching, callback)
        syncHeadphones(matching, callback)
        syncScreen(matching, callback)
        syncBattery(matching, callback)
        syncBluetooth(matching, callback)
    }

    override suspend fun shutdown() {
        listOf(::wifiReceiver, ::powerReceiver, ::headphoneReceiver, ::screenReceiver).forEach { ref ->
            ref.get()?.let { runCatching { context.unregisterReceiver(it) } }; ref.set(null)
        }
        batteryReceiver?.let { runCatching { context.unregisterReceiver(it) } }; batteryReceiver = null
        bluetoothReceiver?.let { runCatching { context.unregisterReceiver(it) } }; bluetoothReceiver = null
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        runCatching { context.registerReceiver(receiver, filter, flags) }
            .onFailure { DebugLog.e(name, "registerReceiver failed", it) }
    }

    private fun unregister(ref: kotlin.reflect.KMutableProperty0<BroadcastReceiver?>) {
        ref.get()?.let { runCatching { context.unregisterReceiver(it) } }
        ref.set(null)
    }

    private fun fireMatching(callback: TriggerFireCallback, predicate: (LinearTrigger) -> Boolean) {
        val current = matching
        if (current.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            current.forEach { wf -> if (predicate(wf.trigger)) runCatching { callback.onFire(wf.id, wf.trigger) } }
        }
    }

    // ── WiFi ────────────────────────────────────────────────────────────────

    private fun syncWifi(list: List<LinearWorkflow>, cb: TriggerFireCallback) {
        val needs = list.any { it.trigger is LinearTrigger.WifiConnected || it.trigger is LinearTrigger.WifiDisconnected }
        if (!needs) return unregister(::wifiReceiver)
        if (wifiReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return
                @Suppress("DEPRECATION")
                val info = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                val connected = info?.isConnected == true
                val ssid = currentSsid()
                fireMatching(cb) { t ->
                    (t is LinearTrigger.WifiConnected && connected && (t.ssid == null || t.ssid == ssid)) ||
                        (t is LinearTrigger.WifiDisconnected && !connected && (t.ssid == null || t.ssid == lastSsid))
                }
                if (connected) lastSsid = ssid
            }
        }
        wifiReceiver = r
        register(r, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }

    @Volatile private var lastSsid: String? = null
    private fun currentSsid(): String? = try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        wm.connectionInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")?.takeIf { it != "<unknown ssid>" }
    } catch (_: Exception) { null }

    // ── Power ───────────────────────────────────────────────────────────────

    private fun syncPower(list: List<LinearWorkflow>, cb: TriggerFireCallback) {
        val needs = list.any { it.trigger is LinearTrigger.PowerConnected || it.trigger is LinearTrigger.PowerDisconnected }
        if (!needs) return unregister(::powerReceiver)
        if (powerReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> fireMatching(cb) { it is LinearTrigger.PowerConnected }
                    Intent.ACTION_POWER_DISCONNECTED -> fireMatching(cb) { it is LinearTrigger.PowerDisconnected }
                }
            }
        }
        powerReceiver = r
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        register(r, f)
    }

    // ── Headphones ──────────────────────────────────────────────────────────

    private fun syncHeadphones(list: List<LinearWorkflow>, cb: TriggerFireCallback) {
        val needs = list.any { it.trigger is LinearTrigger.HeadphonesPlugged || it.trigger is LinearTrigger.HeadphonesUnplugged }
        if (!needs) return unregister(::headphoneReceiver)
        if (headphoneReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_HEADSET_PLUG) return
                val state = intent.getIntExtra("state", -1)
                if (state == 1) fireMatching(cb) { it is LinearTrigger.HeadphonesPlugged }
                else if (state == 0) fireMatching(cb) { it is LinearTrigger.HeadphonesUnplugged }
            }
        }
        headphoneReceiver = r
        register(r, IntentFilter(Intent.ACTION_HEADSET_PLUG))
    }

    // ── Screen ──────────────────────────────────────────────────────────────

    private fun syncScreen(list: List<LinearWorkflow>, cb: TriggerFireCallback) {
        val needs = list.any { it.trigger is LinearTrigger.ScreenOn || it.trigger is LinearTrigger.ScreenOff }
        if (!needs) return unregister(::screenReceiver)
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> fireMatching(cb) { it is LinearTrigger.ScreenOn }
                    Intent.ACTION_SCREEN_OFF -> fireMatching(cb) { it is LinearTrigger.ScreenOff }
                }
            }
        }
        screenReceiver = r
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        register(r, f)
    }

    // ── Battery (threshold crossing) ────────────────────────────────────────

    private fun syncBattery(list: List<LinearWorkflow>, cb: TriggerFireCallback) {
        val needs = list.any { it.trigger is LinearTrigger.BatteryBelow || it.trigger is LinearTrigger.BatteryAbove }
        if (!needs) {
            batteryReceiver?.let { runCatching { context.unregisterReceiver(it) } }; batteryReceiver = null
            return
        }
        if (batteryReceiver != null) return
        val r = BatteryReceiver(list, cb)
        batteryReceiver = r
        register(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private inner class BatteryReceiver(
        @Volatile var currentList: List<LinearWorkflow>,
        private val cb: TriggerFireCallback
    ) : BroadcastReceiver() {
        @Volatile private var prevLevel: Int = -1

        override fun onReceive(ctx: Context, intent: Intent) {
            // Update the list reference each tick so sync()'s reassignment is picked up.
            currentList = this@BroadcastTriggerFamily.matching
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            if (prevLevel < 0) { prevLevel = pct; return }   // seed baseline, don't fire on first tick
            val crossedDown = prevLevel >= threshold && pct < threshold
            val crossedUp = prevLevel <= threshold && pct > threshold
            // Check each distinct threshold among the matching workflows.
            currentList.forEach { wf ->
                when (val t = wf.trigger) {
                    is LinearTrigger.BatteryBelow -> if (prevLevel >= t.threshold && pct < t.threshold)
                        scope.launch { runCatching { cb.onFire(wf.id, t) } }
                    is LinearTrigger.BatteryAbove -> if (prevLevel <= t.threshold && pct > t.threshold)
                        scope.launch { runCatching { cb.onFire(wf.id, t) } }
                    else -> Unit
                }
            }
            prevLevel = pct
        }

        private val threshold: Int
            get() = currentList.firstNotNullOfOrNull { wf ->
                (wf.trigger as? LinearTrigger.BatteryBelow)?.threshold
                    ?: (wf.trigger as? LinearTrigger.BatteryAbove)?.threshold
            } ?: 0
    }

    // ── Bluetooth ───────────────────────────────────────────────────────────

    private fun syncBluetooth(list: List<LinearWorkflow>, cb: TriggerFireCallback) {
        val needs = list.any { it.trigger is LinearTrigger.BluetoothConnected || it.trigger is LinearTrigger.BluetoothDisconnected }
        if (!needs) {
            bluetoothReceiver?.let { runCatching { context.unregisterReceiver(it) } }; bluetoothReceiver = null
            return
        }
        if (bluetoothReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val addr = dev?.address
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED ->
                        fireMatching(cb) { it is LinearTrigger.BluetoothConnected && (it.deviceAddress == null || it.deviceAddress == addr) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                        fireMatching(cb) { it is LinearTrigger.BluetoothDisconnected && (it.deviceAddress == null || it.deviceAddress == addr) }
                }
            }
        }
        bluetoothReceiver = r
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        register(r, f)
    }
}
