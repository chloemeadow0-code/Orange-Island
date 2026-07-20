package com.orangeisland.app.sandbox

import android.content.Context

class FdroidSandboxManagerFactory(private val context: Context) : SandboxManagerFactory {
    override fun create(): SandboxManager = ProotSandboxManager(context)
    override fun isAvailable(): Boolean = true
}
