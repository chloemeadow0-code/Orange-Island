package com.orangeisland.app.sandbox

/**
 * Factory for creating [SandboxManager] instances.
 * Each flavor provides its own implementation:
 * - fdroid → FdroidSandboxManagerFactory (creates ProotSandboxManager)
 * - play   → PlaySandboxManagerFactory (creates no-op stub)
 */
interface SandboxManagerFactory {
    /** Create a new SandboxManager instance. */
    fun create(): SandboxManager

    /** Whether the sandbox feature is available in this build. */
    fun isAvailable(): Boolean
}
