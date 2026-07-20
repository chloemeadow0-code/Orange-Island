package com.orangeisland.app.util

import android.content.Context

object DebugLog {
    @Volatile
    var forceEnabled = false
    @Volatile
    private var enabled = true

    fun init(context: Context) {
        enabled = forceEnabled || (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private val active: Boolean get() = forceEnabled || enabled

    fun d(tag: String, msg: String) { if (active) android.util.Log.d(tag, msg) }
    fun d(tag: String, msg: String, tr: Throwable) { if (active) android.util.Log.d(tag, msg, tr) }
    fun e(tag: String, msg: String) { if (active) android.util.Log.e(tag, msg) }
    fun e(tag: String, msg: String, tr: Throwable) { if (active) android.util.Log.e(tag, msg, tr) }
    fun w(tag: String, msg: String) { if (active) android.util.Log.w(tag, msg) }
    fun w(tag: String, msg: String, tr: Throwable) { if (active) android.util.Log.w(tag, msg, tr) }
}
