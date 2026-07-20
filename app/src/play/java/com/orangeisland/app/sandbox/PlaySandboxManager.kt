package com.orangeisland.app.sandbox

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No-op stub for the Play Store flavor — Linux sandbox is not included.
 */
class PlaySandboxManager : SandboxManager {

    override val lastError: String? = null
    private val _terminalOutput = MutableStateFlow("Sandbox not available in this build.")
    override val terminalOutput: StateFlow<String> = _terminalOutput
    private val _isBusy = MutableStateFlow(false)
    override var pendingPkgName: String = ""
    override val isBusy: StateFlow<Boolean> = _isBusy
    private val _isInstallingRootfs = MutableStateFlow(false)
    override val isInstallingRootfs: StateFlow<Boolean> = _isInstallingRootfs
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress
    override fun installRootfs() {}
    private val _packageList = MutableStateFlow<List<SandboxManager.PackageInfo>>(emptyList())
    override val packageList: StateFlow<List<SandboxManager.PackageInfo>> = _packageList
    override suspend fun refreshPackageList() {}
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    override val snackbarMessage: StateFlow<String?> = _snackbarMessage
    override fun installPackage(name: String) {}
    override fun removePackage(name: String) {}
    override fun upgradePackages() {}

    override fun isAvailableSync(): Boolean = false

    override suspend fun isAvailable(): Boolean = false

    override suspend fun install(): Boolean = false

    override suspend fun executeCommand(
        command: String,
        workdir: String,
        timeoutMs: Int
    ): SandboxManager.SandboxResult = SandboxManager.SandboxResult(
        stdout = "",
        stderr = "Linux Sandbox is not available in this build.",
        exitCode = -1
    )

    override suspend fun fileRead(
        path: String,
        offset: Long,
        limit: Long
    ): String = throw UnsupportedOperationException("Sandbox not available")

    override suspend fun fileWrite(path: String, content: String): String? =
        "Sandbox not available in this build"

    override suspend fun fileGlob(pattern: String, basePath: String, depth: Int?): List<String> =
        emptyList()

    override suspend fun fileGrep(
        pattern: String,
        basePath: String,
        fileGlob: String
    ): Result<List<SandboxManager.GrepMatch>> =
        Result.success(emptyList())

    override suspend fun fileEdit(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): SandboxManager.FileEditResult = SandboxManager.FileEditResult(
        replaced = 0,
        error = "Sandbox not available in this build"
    )

    override suspend fun apkInstall(packageName: String, onProgress: (String) -> Unit): Boolean { onProgress("Sandbox not available"); return false }

    override suspend fun apkList(): List<SandboxManager.PackageInfo> = emptyList()

    override suspend fun apkDelete(packageName: String): Boolean = false

    override suspend fun apkUpgrade(onProgress: (String) -> Unit): Int = 0

    override suspend fun getDiskUsageMB(): Long = 0L

    override fun getSandboxHomeDir(): File? = null
    override val sandboxHomePath: String? = null
    override suspend fun reset(): Boolean = false
    override fun close() {}
}
