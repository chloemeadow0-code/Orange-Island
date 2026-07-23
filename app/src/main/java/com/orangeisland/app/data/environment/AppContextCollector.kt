package com.orangeisland.app.data.environment

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.util.DebugLog
import com.orangeisland.app.workflow.trigger.AppForegroundDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Collects environment changes (app foreground, model switch, prompt switch, wallpaper,
 * theme, battery, WiFi, Bluetooth) into a fixed-size ring buffer and formats a snapshot
 * string for injection into the system prompt via the `{app_context}` predefined variable.
 *
 * Lifecycle: call [start] when the app becomes active (e.g. MainActivity.onResume) and
 * [stop] when it leaves (e.g. MainActivity.onPause). Internally listens to the
 * `environmentAwarenessEnabled` setting and only subscribes to event sources when on.
 */
class AppContextCollector(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    private val events = ArrayDeque<EnvironmentEvent>()
    private val maxEvents = 20
    private val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ── Subscription handles ─────────────────────────────────────────────
    private var foregroundRemover: Runnable? = null
    private var modelJob: Job? = null
    private var promptJob: Job? = null
    private var themeJob: Job? = null
    private var dynamicColorJob: Job? = null
    private var colorSchemeJob: Job? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    // ── Deduplication state ──────────────────────────────────────────────
    private var lastModel: String? = null
    private var lastPromptId: String? = null
    private var lastTheme: String? = null
    private var lastDynamicColor: Boolean? = null
    private var lastColorScheme: String? = null
    private var lastBatteryCharging: Boolean = false
    private var lastBatteryLevel: Int = -1
    private var lastWifiSsid: String = ""

    // ── Master switch listener ──────────────────────────────────────────
    private var enabledJob: Job? = null

    /** Start watching the enable-switch and begin/stop collection accordingly. */
    fun start() {
        if (enabledJob != null) return
        enabledJob = scope.launch {
            settingsRepository.environmentAwarenessEnabled.collect { enabled ->
                if (enabled) beginCollecting() else endCollecting()
            }
        }
    }

    /** Tear down every subscription and the master switch listener. */
    fun stop() {
        enabledJob?.cancel()
        enabledJob = null
        endCollecting()
    }

    /** Formats the most recent events as a bullet list, or empty string if none. */
    fun getSnapshot(): String {
        val copy = synchronized(events) { events.toList() }
        if (copy.isEmpty()) return ""
        return copy.joinToString("\n") {
            "• ${timeSdf.format(Date(it.timestamp))} ${it.description}"
        }
    }

    // ── Internal ────────────────────────────────────────────────────────

    @Synchronized
    private fun pushEvent(type: EnvironmentEventType, description: String) {
        events.add(EnvironmentEvent(type, System.currentTimeMillis(), description))
        while (events.size > maxEvents) events.removeFirst()
        DebugLog.d("AppContext", "[$type] $description")
    }

    private fun beginCollecting() {
        if (foregroundRemover != null) return // already running

        // 1. App foreground transitions
        foregroundRemover = AppForegroundDispatcher.subscribe { pkg ->
            if (!pkg.isNullOrBlank()) {
                pushEvent(
                    EnvironmentEventType.APP_FOREGROUND_CHANGED,
                    "前台应用：$pkg"
                )
            }
        }

        // 2. Model changes
        lastModel = settingsRepository.selectedModel.value
        modelJob = scope.launch {
            settingsRepository.selectedModel.collect { model ->
                if (model != lastModel) {
                    lastModel = model
                    pushEvent(EnvironmentEventType.MODEL_CHANGED, "模型切换为：$model")
                }
            }
        }

        // 3. System prompt changes
        lastPromptId = settingsRepository.activeSystemPromptId.value
        promptJob = scope.launch {
            settingsRepository.activeSystemPromptId.collect { promptId ->
                if (promptId != lastPromptId) {
                    val title = settingsRepository.systemPrompts.value
                        .find { it.id == promptId }?.title ?: "未命名"
                    lastPromptId = promptId
                    pushEvent(EnvironmentEventType.SYSTEM_PROMPT_CHANGED, "提示词切换为：$title")
                }
            }
        }

        // 4. Theme mode
        lastTheme = settingsRepository.themeMode.value
        themeJob = scope.launch {
            settingsRepository.themeMode.collect { theme ->
                if (theme != lastTheme) {
                    lastTheme = theme
                    val label = when (theme) {
                        "LIGHT" -> "浅色模式"
                        "DARK" -> "深色模式"
                        else -> "跟随系统"
                    }
                    pushEvent(EnvironmentEventType.THEME_CHANGED, "主题切换为：$label")
                }
            }
        }

        // 5. Dynamic color
        lastDynamicColor = settingsRepository.dynamicColor.value
        dynamicColorJob = scope.launch {
            settingsRepository.dynamicColor.collect { enabled ->
                if (enabled != lastDynamicColor) {
                    lastDynamicColor = enabled
                    pushEvent(
                        EnvironmentEventType.THEME_CHANGED,
                        if (enabled) "动态取色已开启" else "动态取色已关闭"
                    )
                }
            }
        }

        // 6. Color scheme
        lastColorScheme = settingsRepository.colorScheme.value
        colorSchemeJob = scope.launch {
            settingsRepository.colorScheme.collect { scheme ->
                if (scheme != lastColorScheme) {
                    lastColorScheme = scheme
                    pushEvent(EnvironmentEventType.THEME_CHANGED, "配色方案切换为：$scheme")
                }
            }
        }

        // 7. OS broadcasts (wallpaper, battery, WiFi, Bluetooth)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_WALLPAPER_CHANGED -> {
                        pushEvent(EnvironmentEventType.WALLPAPER_CHANGED, "壁纸已更换")
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val pct = if (scale > 0) (level * 100 / scale) else -1
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                                || status == BatteryManager.BATTERY_STATUS_FULL

                        if (isCharging != lastBatteryCharging) {
                            lastBatteryCharging = isCharging
                            val label = if (isCharging) "开始充电" else "停止充电"
                            pushEvent(EnvironmentEventType.BATTERY_CHANGED, "$label（电量 $pct%）")
                        } else if (pct >= 0 && lastBatteryLevel >= 0 && abs(pct - lastBatteryLevel) >= 15) {
                            lastBatteryLevel = pct
                            pushEvent(EnvironmentEventType.BATTERY_CHANGED, "电量：$pct%")
                        }
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        pushEvent(EnvironmentEventType.POWER_CONNECTED, "已连接电源")
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        pushEvent(EnvironmentEventType.POWER_DISCONNECTED, "已断开电源")
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        val info = wifiMgr?.connectionInfo
                        val ssid = info?.ssid?.replace("\"", "") ?: ""
                        if (ssid.isNotBlank()
                            && ssid != "<unknown ssid>"
                            && ssid != "0x"
                            && ssid != lastWifiSsid
                        ) {
                            lastWifiSsid = ssid
                            pushEvent(EnvironmentEventType.WIFI_CHANGED, "WiFi 已连接：$ssid")
                        }
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR
                        )
                        val label = when (state) {
                            BluetoothAdapter.STATE_ON -> "蓝牙已开启"
                            BluetoothAdapter.STATE_OFF -> "蓝牙已关闭"
                            else -> return
                        }
                        pushEvent(EnvironmentEventType.BLUETOOTH_CHANGED, label)
                    }
                }
            }
        }
        broadcastReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun endCollecting() {
        foregroundRemover?.run()
        foregroundRemover = null
        modelJob?.cancel(); modelJob = null
        promptJob?.cancel(); promptJob = null
        themeJob?.cancel(); themeJob = null
        dynamicColorJob?.cancel(); dynamicColorJob = null
        colorSchemeJob?.cancel(); colorSchemeJob = null
        broadcastReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
            broadcastReceiver = null
        }
    }
}
