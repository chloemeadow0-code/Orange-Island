package com.orangeisland.app.sandbox

class PlaySandboxManagerFactory : SandboxManagerFactory {
    override fun create(): SandboxManager = PlaySandboxManager()
    override fun isAvailable(): Boolean = false
}
