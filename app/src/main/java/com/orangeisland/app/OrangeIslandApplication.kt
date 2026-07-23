package com.orangeisland.app

import android.app.Application
import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.di.AppContainer
import com.orangeisland.app.util.CrashReporter

/**
 * Application entry point. Installs the crash reporter before any other component runs so
 * that crashes occurring during startup are captured as well.
 *
 * Also owns the app-lifetime [AppContainer]. Hoisting it here (rather than building it
 * per-Activity in MainActivity) keeps the Supabase auth session and all shared singletons
 * stable across configuration changes / activity recreation.
 */
class OrangeIslandApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        UsageLogManager.init(this)
        // Start the workflow trigger host so a device signal (boot, schedule, WiFi, …) fires its
        // matching linear workflow through the runner. Idempotent; wrapped so a host failure can
        // never prevent the rest of app init.
        runCatching { container.startTriggerHost() }
            .onFailure { com.orangeisland.app.util.DebugLog.e("OrangeIslandApp", "trigger host start failed", it) }
    }
}
