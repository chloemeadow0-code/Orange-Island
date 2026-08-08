package com.orangeisland.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.orangeisland.app.data.UsageLogManager
import com.orangeisland.app.di.AppContainer
import com.orangeisland.app.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Installs the crash reporter before any other component runs so
 * that crashes occurring during startup are captured as well.
 *
 * Also owns the app-lifetime [AppContainer]. Hoisting it here (rather than building it
 * per-Activity in MainActivity) keeps the Supabase auth session and all shared singletons
 * stable across configuration changes / activity recreation.
 */
class OrangeIslandApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

    /** App-lifetime scope for deferred startup work. SupervisorJob so one failing
     *  startup task can't cancel the siblings sharing the scope. */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Global Coil [ImageLoader] with a GIF decoder registered so animated GIFs
     * (stickers, markdown `![](url)` images, attachment thumbnails) play across
     * the whole app. Coil builds the singleton lazily on first use and routes
     * here automatically — no per-call-site wiring required.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        UsageLogManager.init(this)
        // 三个启动调用全部挪到后台协程。它们本身幂等，但同步调用会当场求值 AppContainer
        // 的一整条 by lazy 链——startTriggerHost() → triggerHost → workflowRepository →
        // ChatDatabase.build()（Room builder + 全量迁移表构建）+ workflowDao()；
        // rescheduleGraphWorkflows() 的内部逻辑虽在 appScope.launch 里，但外层调用本身
        // 就要先同步求值 workflowRepository；startPetController() → petController →
        // settingsRepository。之前全部压在 Application.onCreate() 的主线程上，直接拖慢
        // 冷启动首帧。（PluginLoader / ToolDispatcher / ProviderRegistry 不在此链上——
        // 它们要等某个 trigger 真正命中、构建 workflowRunner() 时才求值。）
        //
        // 推迟执行的时序安全性：
        //  - Boot/升级广播有独立的 WorkManager 兜底（BootSignalSource.onBoot 的冷路径
        //    enqueueDiscover 让 BootFireWorker 自己读库点火），不依赖 host 已启动；
        //  - PetBootReceiver 有自己的 pet 自启路径，不经过 PetController；
        //  - petEnabled 是 Eagerly StateFlow、observeEnabledLinear 是 Room Flow，新收集者
        //    立即拿到当前值——host/controller 晚启动几百 ms 不会漏掉启动间隙里的状态切换；
        //  - rescheduleGraphWorkflows 只是 WorkManager 的重排刷新，迟几百 ms 无害；
        //  - 用户手动跑 workflow 走 WorkflowViewModel/workflowRunner 的独立构建路径，
        //    与 triggerHost 是否已 start 无关，不存在"操作早于启动完成"的状态不一致。
        // 保持在同一协程内顺序执行，维持原有的先后次序。
        startupScope.launch {
            // Start the workflow trigger host so a device signal (boot, schedule, WiFi, …) fires its
            // matching linear workflow through the runner. Idempotent; wrapped so a host failure can
            // never prevent the rest of app init.
            runCatching { container.startTriggerHost() }
                .onFailure { com.orangeisland.app.util.DebugLog.e("OrangeIslandApp", "trigger host start failed", it) }
            // Re-enqueue graph-mode Schedule triggers — they have no live Flow reconciler like linear
            // workflows do, so a cold start (reboot / upgrade / fresh install) needs this refresh or
            // previously-saved graph schedules would silently never fire.
            runCatching { container.rescheduleGraphWorkflows() }
                .onFailure { com.orangeisland.app.util.DebugLog.e("OrangeIslandApp", "graph reschedule failed", it) }
            // Start observing the desktop-pet setting so the floating companion comes
            // back (if enabled) whenever the process starts. Idempotent; mirrors the
            // trigger-host start above.
            runCatching { container.startPetController() }
                .onFailure { com.orangeisland.app.util.DebugLog.e("OrangeIslandApp", "pet controller start failed", it) }
        }
    }
}
