package com.orangeisland.app.data.environment

enum class EnvironmentEventType {
    APP_FOREGROUND_CHANGED,
    MODEL_CHANGED,
    SYSTEM_PROMPT_CHANGED,
    WALLPAPER_CHANGED,
    THEME_CHANGED,
    BATTERY_CHANGED,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    WIFI_CHANGED,
    BLUETOOTH_CHANGED,
}

data class EnvironmentEvent(
    val type: EnvironmentEventType,
    val timestamp: Long,
    val description: String,
)
