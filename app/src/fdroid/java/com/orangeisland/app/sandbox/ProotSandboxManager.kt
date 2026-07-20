package com.orangeisland.app.sandbox

import android.content.Context
import android.util.Log
import com.orangeisland.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.FileSystems
class ProotSandboxManager(private val context: Context) : SandboxManager {

    // Pin to the stable v3.21 branch to match the downloaded minirootfs (3.21.0). Using edge here
    // caused `apk upgrade` to pull divergent packages (e.g. yash-binsh vs busybox-binsh /bin/sh
    // conflict) and rotates signing keys; the stable branch avoids both.
    // China mirror (TUNA) — the upstream dl-cdn.alpinelinux.org is slow/unreliable from
    // mainland China; this mirror carries the exact same packages (same signing keys, same
    // APKINDEX). Pin to the stable v3.21 branch to match the bundled minirootfs (3.21.0).
    private val alpineMirror = "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.21/main"
    // Base rootfs is fetched on-device at install time (not bundled in the APK), then verified
    // against this pinned SHA-256 before extraction. Stable v3.21 release URL.
    private val rootfsUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz"
    private val rootfsSha256 = "f31202c4070c4ef7de9e157e1bd01cb4da3a2150035d74ea5372c5e86f1efac1"
    private var sandboxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _terminalOutput = MutableStateFlow("")
    override val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    override val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val _isInstallingRootfs = MutableStateFlow(false)
    override val isInstallingRootfs: StateFlow<Boolean> = _isInstallingRootfs.asStateFlow()
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()
    private val _packageList = MutableStateFlow<List<SandboxManager.PackageInfo>>(emptyList())
    override val packageList: StateFlow<List<SandboxManager.PackageInfo>> = _packageList.asStateFlow()

    override suspend fun refreshPackageList() {
        if (isAvailable()) _packageList.value = apkList()
    }
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    override val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()
    override var pendingPkgName: String = ""

    private val rootfsDir: File = File(context.filesDir, "alpine-rootfs")
    private val homeMountDir: File = File(context.filesDir, "sandbox-home")
    private val homeMountPath = "/home/orangeisland"
    private val metadataDir: File = File(rootfsDir, "etc/orangeisland")
    private val baseWorldFile: File = File(metadataDir, "base-world")
    private val explicitPackagesFile: File = File(metadataDir, "explicit-packages")
    private val defaultBaseWorld = linkedSetOf("alpine-baselayout", "alpine-keys", "apk-tools", "busybox", "libc-utils")
    private val packageNameRegex = Regex("^[A-Za-z0-9][A-Za-z0-9+_.:-]*$")

    private val prootExecPath: String by lazy {
        // Force System.loadLibrary to trigger extraction from APK.
        // Without this, the .so may not be in nativeLibraryDir at runtime.
        try { System.loadLibrary("orangeisland_proot") } catch (_: Throwable) {}
        "${context.applicationInfo.nativeLibraryDir}/libproot_exec.so"
    }

    override var lastError: String? = null

    /**
     * Rewrite root's home entry in /etc/passwd from /root to /home/orangeisland.
     * Some programs call getpwuid(0) instead of reading $HOME, so the passwd
     * entry must match the HOME env var for consistent behaviour (shell, git, SSH, etc.).
     * This is a direct file edit — no proot needed, idempotent, and fast.
     */
    private fun ensureRootHome() {
        val passwdFile = File(rootfsDir, "etc/passwd")
        if (!passwdFile.isFile) return
        val content = passwdFile.readText()
        if ("root:x:0:0:root:/home/orangeisland:" in content) return // already correct
        val updated = content.replace(
            Regex("^(root:x:0:0:root:)/root(:)", RegexOption.MULTILINE),
            "$1/home/orangeisland$2"
        )
        if (updated != content) {
            passwdFile.writeText(updated)
        }
    }

    private fun ensureShell(): Boolean {
        val sh = File(rootfsDir, "bin/sh")
        if (sh.exists()) return true
        try {
            val busybox = File(rootfsDir, "bin/busybox")
            if (busybox.isFile && busybox.canRead()) {
                // Delete broken symlink if present (exists()=false but symlink entry exists)
                sh.delete()
                busybox.copyTo(sh, false); sh.setExecutable(true)
                return true
            }
        } catch (_: Throwable) { sh.delete() }
        return false
    }

    override fun isAvailableSync(): Boolean {
        if (!rootfsDir.isDirectory) return false
        if (!File(rootfsDir, "bin/sh").exists()) return false
        return listOf("lib/ld-musl-aarch64.so.1", "usr/lib/ld-musl-aarch64.so.1")
            .map { File(rootfsDir, it) }.any { it.exists() }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!rootfsDir.isDirectory) { lastError = "rootfs not found: ${rootfsDir.absolutePath}"; return@withContext false }
        if (!ensureShell()) { lastError = "/bin/sh missing"; return@withContext false }
        val linker = listOf("lib/ld-musl-aarch64.so.1", "usr/lib/ld-musl-aarch64.so.1").map { File(rootfsDir, it) }.any { it.exists() }
        if (!linker) { lastError = "musl linker missing"; return@withContext false }
        ensureSandboxMountTargets()
        ensurePackageMetadata()
        ensureRootHome()
        true
    }

    override suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (rootfsDir.exists()) { rootfsDir.deleteRecursively(); if (rootfsDir.exists()) { error("Cannot delete stale rootfs") } }
            rootfsDir.mkdirs()

            val tmpTar = File(context.filesDir, "alpine-rootfs.tar.gz")
            try {
                // Rootfs is bundled in the APK assets (alpine-rootfs.bundle — a gzip'd tar
                // renamed to a neutral extension so AAPT2 doesn't auto-decompress it) so the
                // sandbox installs instantly and reliably without depending on the Alpine CDN,
                // important on networks where dl-cdn.alpinelinux.org is slow/blocked.
                _terminalOutput.value += "Extracting bundled Alpine minirootfs…\n"
                copyAssetTo("alpine-rootfs.bundle", tmpTar)
                // Verify the bundled tarball against the pinned SHA-256 (tamper/integrity check).
                val got = sha256OfFile(tmpTar)
                if (!got.equals(rootfsSha256, ignoreCase = true)) {
                    error("bundled rootfs checksum mismatch (expected $rootfsSha256, got $got)")
                }
                _downloadProgress.value = null
                _terminalOutput.value += "Extracting rootfs…\n"
                java.util.zip.GZIPInputStream(tmpTar.inputStream()).use { gz ->
                    org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gz).use { tar -> extractTarEntries(tar, rootfsDir) }
                }
            } finally { tmpTar.delete() }

            File(rootfsDir, "tmp").mkdirs()
            File(rootfsDir, "run").mkdirs()
            ensureSandboxMountTargets()
            listOf("var/cache/apk", "etc/apk/cache", "var/lock").forEach { File(rootfsDir, it).mkdirs() }
            val rc = File(rootfsDir, "etc/resolv.conf"); rc.parentFile?.mkdirs()
            // Use China-accessible DNS — 8.8.8.8/1.1.1.1 are blocked from mainland China,
            // which made every name resolution inside the sandbox fail.
            rc.writeText("nameserver 223.5.5.5\nnameserver 119.29.29.29\n")
            // Alpine repository config
            val repos = File(rootfsDir, "etc/apk/repositories"); repos.parentFile?.mkdirs()
            repos.writeText("$alpineMirror\n")
            // Ensure all binaries are executable recursively
            listOf("bin", "usr/bin", "sbin", "usr/sbin", "usr/libexec").forEach { dir ->
                val d = File(rootfsDir, dir)
                if (d.isDirectory) d.walkTopDown().filter { it.isFile }.forEach { it.setExecutable(true) }
            }
            // No auto `apk upgrade` here: the freshly-downloaded minirootfs is already a coherent
            // pinned release. Running upgrade immediately makes apk re-resolve /bin/sh and dead-locks
            // on the busybox-binsh vs yash-binsh `cmd:sh` conflict. Packages upgrade on demand.
            captureBaseWorld(force = true)
            writeExplicitPackages(emptySet())
            isAvailable()
        } catch (e: Throwable) { e.printStackTrace(); lastError = e.message; false }
    }

    override fun installRootfs() {
        if (_isInstallingRootfs.value) return
        sandboxScope.launch {
            _isInstallingRootfs.value = true
            _downloadProgress.value = null
            _terminalOutput.value = ""
            _packageList.value = emptyList()
            try {
                // NOTE: don't call reset() here — it cancels sandboxScope (i.e. this very
                // coroutine). install() already wipes any stale rootfs before extracting.
                val ok = install()
                if (ok) refreshPackageList()
            } catch (e: Throwable) {
                e.printStackTrace(); lastError = e.message
            } finally {
                _isInstallingRootfs.value = false
                _downloadProgress.value = null
            }
        }
    }

    /**
     * Open an HttpURLConnection with a browser-like User-Agent and generous timeouts.
     * Some Alpine mirrors (e.g. TUNA) reject requests whose UA is the Java default
     * ("Java/xx") with HTTP 403, so every outbound request must go through here.
     */
    private fun openHttp(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "OrangeIsland/1.0 (Android; Linux)")
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.instanceFollowRedirects = true
        return conn
    }

    /** Download [url] to [dest], streaming SHA-256 + progress, then verify against [rootfsSha256]. */
    private fun downloadRootfs(url: String, dest: File) {
        val conn = openHttp(url)
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} fetching rootfs")
            val total = conn.contentLengthLong
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        downloaded += n
                        _downloadProgress.value = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(rootfsSha256, ignoreCase = true)) {
                dest.delete()
                error("rootfs checksum mismatch (expected $rootfsSha256, got $hex)")
            }
        } finally { conn.disconnect() }
    }

    /** Copy a bundled asset [assetName] to [dest], streaming. */
    private fun copyAssetTo(assetName: String, dest: File) {
        context.assets.open(assetName).use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        if (!dest.exists() || dest.length() == 0L) error("Failed to copy bundled asset: $assetName")
    }

    /** SHA-256 (lowercase hex) of a file. */
    private fun sha256OfFile(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun installPackage(name: String) {
        if (_isBusy.value) return
        sandboxScope.launch {
            _terminalOutput.value = ""
            _isBusy.value = true
            lastError = null
            try {
                val ok = apkInstall(name) { _terminalOutput.value += it + "\n" }
                ensureShell()
                _packageList.value = apkList()
                _terminalOutput.value += if (ok) "✓ Installed $name\n" else "✗ Failed\n"
                _snackbarMessage.value = if (ok) context.getString(R.string.sandbox_snackbar_installed, name) else context.getString(R.string.sandbox_snackbar_install_failed, name)
            } catch (e: Throwable) { ensureShell()
                _packageList.value = apkList()
                _terminalOutput.value += "✗ Error: ${e.message}\n"
                _snackbarMessage.value = context.getString(R.string.sandbox_snackbar_error, e.message ?: "")
            } finally { _isBusy.value = false }
        }
    }

    override fun removePackage(name: String) {
        if (_isBusy.value) return
        sandboxScope.launch {
            _terminalOutput.value = ""
            _isBusy.value = true
            lastError = null
            try {
                val ok = apkDelete(name)
                _terminalOutput.value += if (ok) "✓ Removed $name\n" else "✗ Failed to remove $name\n"
                _snackbarMessage.value = if (ok) context.getString(R.string.sandbox_snackbar_removed, name) else context.getString(R.string.sandbox_snackbar_remove_failed, name)
            } catch (e: Throwable) {
                _terminalOutput.value += "✗ Error: ${e.message}\n"
                _snackbarMessage.value = context.getString(R.string.sandbox_snackbar_error, e.message ?: "")
            } finally { ensureShell(); _isBusy.value = false; _packageList.value = apkList() }
        }
    }

    override fun upgradePackages() {
        if (_isBusy.value) return
        sandboxScope.launch {
            _terminalOutput.value = ""
            _isBusy.value = true
            lastError = null
            try {
                val upgraded = apkUpgrade { _terminalOutput.value += it + "\n" }
                ensureShell()
                _packageList.value = apkList()
                val ok = lastError == null
                _terminalOutput.value += when {
                    upgraded > 0 -> "✓ Upgraded $upgraded packages\n"
                    ok -> "✓ Packages already up to date\n"
                    else -> "✗ Upgrade failed\n"
                }
                _snackbarMessage.value = when {
                    upgraded > 0 -> context.getString(R.string.sandbox_snackbar_upgrade_done, upgraded)
                    ok -> context.getString(R.string.sandbox_snackbar_upgrade_none)
                    else -> context.getString(R.string.sandbox_snackbar_upgrade_failed)
                }
            } catch (e: Throwable) {
                _terminalOutput.value += "✗ Error: ${e.message}\n"
                _snackbarMessage.value = context.getString(R.string.sandbox_snackbar_error, e.message ?: "")
            } finally { ensureShell(); _isBusy.value = false; _packageList.value = apkList() }
        }
    }

    override fun getSandboxHomeDir(): File? = homeMountDir

    override val sandboxHomePath: String? = homeMountPath

    override fun close() {
        sandboxScope.cancel()
    }
    override suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        sandboxScope.cancel(); sandboxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        _terminalOutput.value = ""
        _packageList.value = emptyList()
        try {
            for (i in 1..3) {
                rootfsDir.deleteRecursively()
                if (!rootfsDir.exists()) break
                kotlinx.coroutines.delay(200)
            }
            prootBin.delete()
            _snackbarMessage.value = context.getString(R.string.sandbox_snackbar_reset)
            true
        } catch (e: Throwable) { _snackbarMessage.value = context.getString(R.string.sandbox_snackbar_reset_failed); false }
    }

    // ── Shell Execution ─────────────────────────────────

    /** Path to proot binary, extracted from assets — Termux-style. */
    private val prootBin: File = File(context.filesDir, "bin/proot")

    private val prootPath: String by lazy {
        "${context.applicationInfo.nativeLibraryDir}/libproot_exec.so"
    }

    // Copy libtalloc.so -> libtalloc.so.2 in writable dir for linker resolution.
    // Android linker searches by exact filename, not SONAME.
    // Kai's proot DT_NEEDED is "libtalloc.so.2" but jniLibs has "libtalloc.so".
    private val tallocDir: File by lazy {
        File(context.filesDir, "lib").apply { mkdirs() }
    }
    private fun ensureTalloc(): String {
        val src = File(context.applicationInfo.nativeLibraryDir, "libtalloc.so")
        val dst = File(tallocDir, "libtalloc.so.2")
        if (!dst.exists() && src.exists()) {
            src.copyTo(dst)
        }
        return tallocDir.absolutePath
    }

    private fun executeRaw(command: String, workdir: String = homeMountPath, timeoutMs: Int = 30000): SandboxManager.SandboxResult {
        ensureShell()
        ensureSandboxMountTargets()
        val tmpDir = File(rootfsDir, "tmp").apply { mkdirs() }.absolutePath
        val resolvedWorkdir = workdir.ifBlank { homeMountPath }
        val args = listOf(prootPath,
            "--rootfs=" + rootfsDir.absolutePath,
            "--bind=/dev", "--bind=/proc", "--bind=/sys",
            "--bind=/dev/urandom:/dev/random",
            "--bind=${homeMountDir.absolutePath}:$homeMountPath",
            "-w", resolvedWorkdir,
            "-0", "--link2symlink", "--kill-on-exit", "-L",
            "/bin/sh", "-c", command
        )
        return try {
            val libDir = context.applicationInfo.nativeLibraryDir
            val tallocLibDir = ensureTalloc()
            val ldPath = "$tallocLibDir:$libDir"
            val pb = ProcessBuilder(args).redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] = ldPath
            pb.environment()["PROOT_LOADER"] = "$libDir/libproot_loader.so"
            pb.environment()["PROOT_TMP_DIR"] = tmpDir
            pb.environment()["HOME"] = homeMountPath
            pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(timeoutMs.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!ok) { p.destroyForcibly(); SandboxManager.SandboxResult(out, "Timed out", -1) }
            else SandboxManager.SandboxResult(out, "", p.exitValue())
        } catch (e: Throwable) { SandboxManager.SandboxResult("", e.message ?: "proot failed", -1) }
    }

    override suspend fun executeCommand(cmd: String, wd: String, to: Int): SandboxManager.SandboxResult {
        if (!isAvailable()) return SandboxManager.SandboxResult("", "Sandbox not installed", -1)
        return executeRaw(cmd, wd.ifBlank { homeMountPath }, to)
    }

    // ── File Operations ────────────────────────────────

    override suspend fun fileRead(path: String, offset: Long, limit: Long): String = withContext(Dispatchers.IO) {
        val f = resolvePath(path); if (!f.exists()) throw IllegalStateException("File not found: $path")
        val fileSize = f.length().toInt()
        val s = offset.coerceIn(0, fileSize.toLong()).toInt()
        val max = com.orangeisland.app.util.Constants.MAX_TOOL_RESULT_LENGTH
        val e = if (limit > 0) minOf((s + limit).toInt(), fileSize)
                else minOf(s + max, fileSize)
        val len = e - s
        val buf = ByteArray(len)
        f.inputStream().use { it.skip(s.toLong()); it.read(buf) }
        String(buf, Charsets.UTF_8)
    }

    override suspend fun fileWrite(path: String, content: String): String? = withContext(Dispatchers.IO) {
        try { val f = resolvePath(path); f.parentFile?.mkdirs(); f.writeText(content, Charsets.UTF_8); null }
        catch (e: Throwable) { "Sandbox file write failed: ${e.message}" }
    }

    override suspend fun fileGlob(pattern: String, basePath: String, depth: Int?): List<String> = withContext(Dispatchers.IO) {
        val base = resolveSandboxPath(if (basePath.isBlank()) "/" else basePath)
        val files = mutableListOf<String>()
        // null = legacy full recursion; <=0 = explicit unlimited; >=1 = max levels.
        val remaining = if (depth == null || depth <= 0) -1 else depth
        walkVirtualFiles(base.file, files, base.physicalRoot.canonicalPath, base.virtualRoot, remaining)
        globMatch(files, pattern)
    }

    override suspend fun fileGrep(pattern: String, basePath: String, fileGlob: String): Result<List<SandboxManager.GrepMatch>> = withContext(Dispatchers.IO) {
        try {
            val regex = try { Regex(pattern) } catch (e: Throwable) { Regex(java.util.regex.Pattern.quote(pattern)) }
            val files = if (fileGlob.isNotBlank()) fileGlob(fileGlob, basePath)
            else {
                val b = resolveSandboxPath(if (basePath.isBlank()) "/" else basePath)
                val a = mutableListOf<String>()
                walkVirtualFiles(b.file, a, b.physicalRoot.canonicalPath, b.virtualRoot)
                a
            }
            val matches = mutableListOf<SandboxManager.GrepMatch>()
            for (file in files) {
                try {
                    val resolved = if (file.startsWith("/")) resolvePath(file) else resolvePath("/$file")
                    if (!resolved.exists() || resolved.length() > 500_000L) continue
                    val text = resolved.readText(Charsets.UTF_8)
                    // Skip binary files: a NUL byte in the content is the standard
                    // heuristic grep itself uses to avoid emitting garbage matches.
                    if (text.contains('\u0000')) continue
                    text.lines().forEachIndexed { i, line ->
                        if (regex.containsMatchIn(line)) matches.add(SandboxManager.GrepMatch(path = file, line = i + 1, content = line.take(500)))
                    }
                } catch (_: Throwable) {}
            }
            Result.success(matches)
        } catch (e: Throwable) { Result.failure(e) }
    }

    override suspend fun fileEdit(path: String, oldString: String, newString: String, replaceAll: Boolean): SandboxManager.FileEditResult = withContext(Dispatchers.IO) {
        try {
            val f = resolvePath(path); if (!f.exists()) return@withContext SandboxManager.FileEditResult(0, "File not found: $path")
            if (f.length() > com.orangeisland.app.util.Constants.MAX_FILE_CONTENT_READ_LENGTH) return@withContext SandboxManager.FileEditResult(0, "File too large to edit (>${com.orangeisland.app.util.Constants.MAX_FILE_CONTENT_READ_LENGTH / 1000}KB)")
            val content = f.readText(Charsets.UTF_8); val count = content.split(oldString).size - 1
            if (count == 0) SandboxManager.FileEditResult(0, "old_string not found in file")
            else if (count > 1 && !replaceAll) SandboxManager.FileEditResult(0, "Found $count matches. Use replace_all=true.")
            else { f.writeText(content.replace(oldString, newString), Charsets.UTF_8); SandboxManager.FileEditResult(if (replaceAll) count else 1) }
        } catch (e: Throwable) { SandboxManager.FileEditResult(0, "Sandbox file edit failed: ${e.message}") }
    }

    // ── Package Management ──────────────────────────────
    // Downloads target + all transitive deps + stale base-package upgrades via
    // Android HTTP (works with VPN/Clash), then single apk add --no-network.

    override suspend fun apkInstall(packageName: String, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) { onProgress("Sandbox not installed"); return@withContext false }
        val requested = try {
            sanitizePackageName(packageName)
        } catch (e: IllegalArgumentException) {
            onProgress("FAIL: ${e.message}")
            lastError = e.message
            return@withContext false
        }
        lastError = null
        ensurePackageMetadata()

        // 1. Download + parse repo index
        onProgress("Fetching package index...")
        val indexUrl = "$alpineMirror/aarch64/APKINDEX.tar.gz"
        val indexFile = File(context.filesDir, "APKINDEX.tar.gz")
        try {
            val conn = openHttp(indexUrl)
            onProgress("Connecting to ${conn.url.host}...")
            val code = conn.responseCode
            onProgress("HTTP $code (${conn.contentLength} bytes)")
            if (code != 200) { onProgress("FAIL: HTTP $code"); lastError = "HTTP $code from $indexUrl"; return@withContext false }
            conn.inputStream.use { i -> indexFile.outputStream().use { o -> i.copyTo(o) } }
        }
        catch (e: Throwable) { onProgress("FAIL: ${e.javaClass.simpleName}: ${e.message}"); lastError = "${e.javaClass.simpleName}: ${e.message}"; return@withContext false }

        val repoPkgs: Map<String, FullPkgEntry>
        val soToPkg: Map<String, String>
        try {
            val (r, s) = parseFullApkIndex(indexFile)
            repoPkgs = r; soToPkg = s
        } catch (e: Throwable) {
            onProgress("FAIL: parse index — ${e.javaClass.simpleName}: ${e.message}")
            lastError = "Parse index: ${e.message}"; indexFile.delete(); return@withContext false
        } finally { indexFile.delete() }

        if (requested !in repoPkgs) {
            onProgress("FAIL: package '$requested' not found in index")
            lastError = "Not found: $requested"; return@withContext false
        }

        // 2. Read installed DB — don't reinstall/downgrade existing packages
        val installed = readInstalledVersions()

        // 3. Recursively resolve target + transitive deps.
        // Install if missing; upgrade if repo is newer; NEVER downgrade.
        // Downgrading breaks version constraints of packages that were
        // compiled against a newer version in the rootfs.
        val toInstall = linkedSetOf<String>()
        fun resolve(name: String, visited: MutableSet<String> = mutableSetOf()) {
            if (name in visited || name !in repoPkgs) return
            visited.add(name)
            val instVer = installed[name]
            val repoVer = repoPkgs[name]!!.version
            if (instVer == null || compareAlpineVersions(repoVer, instVer) > 0) toInstall.add(name)
            for (dep in repoPkgs[name]!!.deps) {
                val dn = dep.takeWhile { it != '=' && it != '>' && it != '<' && it != '~' }
                if (dn.isNotEmpty()) {
                    if (dn in repoPkgs) resolve(dn, visited)
                    else soToPkg[dn]?.let { resolve(it, visited) }
                }
            }
        }
        resolve(requested)
        onProgress("${toInstall.size} packages to install")

        if (toInstall.isEmpty()) {
            addExplicitPackage(requested)
            onProgress("$requested is already installed and up to date.")
            return@withContext true
        }

        // 4. Download all .apk files
        val tmpDir = File(rootfsDir, "tmp"); tmpDir.listFiles()?.forEach { it.delete() }; tmpDir.mkdirs()
        val paths = mutableListOf<String>()
        for (name in toInstall) {
            val ver = repoPkgs[name]?.version ?: continue
            val fn = "$name-$ver.apk"; val f = File(context.filesDir, fn)
            if (!f.exists() || f.length() == 0L) {
                onProgress("Downloading $fn...")
                try {
                    val conn = openHttp("$alpineMirror/aarch64/$fn")
                    if (conn.responseCode != 200) { onProgress("HTTP ${conn.responseCode}"); lastError = "HTTP ${conn.responseCode}: $fn"; tmpDir.listFiles()?.forEach { it.delete() }; return@withContext false }
                    conn.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                } catch (ex: Throwable) { onProgress("FAIL: ${ex.message}"); lastError = "Download: ${ex.message}"; tmpDir.listFiles()?.forEach { it.delete() }; return@withContext false }
            }
            val dst = File(tmpDir, fn); f.copyTo(dst, true); f.delete(); paths.add("/tmp/$fn")
        }

        // 5. Pre-install: if we're replacing /bin/sh provider, install that first
        // to avoid post-install scripts failing when busybox-binsh is purged.
        val shPkgs = toInstall.filter { "binsh" in it || it == "yash" }.toList()
        if (shPkgs.isNotEmpty()) {
            val shPaths = paths.filter { p -> shPkgs.any { p.contains(it) } }
            if (shPaths.isNotEmpty()) {
                onProgress("Installing shell provider first...")
                val r = executeRaw("apk add --allow-untrusted --no-network ${shPaths.joinToString(" ") { shellQuote(it) }}", timeoutMs = 60000)
                onProgress(r.stdout)
                paths.removeAll(shPaths)
            }
        }

        // 6. Main install
        onProgress("Installing ${paths.size} packages...")
        val result = if (paths.isNotEmpty()) {
            executeRaw("apk add --allow-untrusted --no-network ${paths.joinToString(" ") { shellQuote(it) }}", timeoutMs = 120000)
        } else {
            SandboxManager.SandboxResult("", "", 0)
        }
        onProgress(result.stdout); tmpDir.listFiles()?.forEach { it.delete() }
        // Verify install — apk may return non-zero on minor post-install script errors
        val installedOk = requested in readInstalledVersions()
        if (!installedOk) { lastError = result.stderr.ifBlank { result.stdout }; return@withContext false }
        addExplicitPackage(requested)
        true
    }

    override suspend fun apkList(): List<SandboxManager.PackageInfo> = withContext(Dispatchers.IO) {
        if (!isAvailable()) { _terminalOutput.value += "[apkList: isAvailable=false]\n"; return@withContext emptyList() }
        try {
            val db = File(rootfsDir, "lib/apk/db/installed")
            if (!db.exists()) { _terminalOutput.value += "[apkList: DB not found at ${db.absolutePath}]\n"; return@withContext emptyList() }
            val content = db.readText(Charsets.UTF_8)
            val pkgs = mutableListOf<SandboxManager.PackageInfo>()
            var n = ""; var v = ""; var d = ""
            content.lines().forEach { line ->
                if (line.startsWith("P:")) n = line.substring(2).trim()
                else if (line.startsWith("V:")) v = line.substring(2).trim()
                else if (line.startsWith("T:")) d = line.substring(2).trim()
                else if (line.isBlank()) { if (n.isNotBlank()) { pkgs.add(SandboxManager.PackageInfo(name = n, version = v, description = d)); n = ""; v = ""; d = "" } }
            }
            if (n.isNotBlank()) pkgs.add(SandboxManager.PackageInfo(name = n, version = v, description = d))
            if (pkgs.isEmpty()) _terminalOutput.value += "[apkList: parsed 0 from ${content.length}B]\n"
            pkgs
        } catch (e: Throwable) { _terminalOutput.value += "[apkList: ${e.message}]\n"; emptyList() }
    }

    override suspend fun apkDelete(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) { _terminalOutput.value += "Sandbox not available\n"; return@withContext false }
        val requested = try {
            sanitizePackageName(packageName)
        } catch (e: IllegalArgumentException) {
            _terminalOutput.value += "FAIL: ${e.message}\n"
            lastError = e.message
            return@withContext false
        }
        lastError = null
        ensurePackageMetadata()
        val installedBefore = readInstalledVersions()
        _terminalOutput.value += "DB has package: ${requested in installedBefore}\n"
        if (requested !in installedBefore) {
            val explicit = readExplicitPackages().apply { remove(requested) }
            writeExplicitPackages(explicit)
            normalizeWorld(explicit)
            return@withContext true
        }

        val baseNames = readBaseWorld().map { worldPackageName(it) }.toSet()
        if (requested in baseNames) {
            lastError = "Refusing to remove base package: $requested"
            _terminalOutput.value += "${lastError}\n"
            return@withContext false
        }

        val previousExplicit = readExplicitPackages()
        val nextExplicit = previousExplicit.toMutableSet().apply { remove(requested) }.toSet()
        writeExplicitPackages(nextExplicit)
        normalizeWorld(nextExplicit)

        _terminalOutput.value += "Running: apk del $requested\n"
        val result = executeRaw("apk del ${shellQuote(requested)}", timeoutMs = 60000)
        _terminalOutput.value += result.stdout
        _terminalOutput.value += if (result.exitCode == 0) "Exit: 0\n" else "Exit: ${result.exitCode}\n"
        val removed = requested !in readInstalledVersions()
        if (!removed) {
            writeExplicitPackages(previousExplicit)
            normalizeWorld(previousExplicit)
            lastError = result.stderr.ifBlank { result.stdout }.ifBlank { "Package was not removed: $requested" }
            return@withContext false
        }
        normalizeWorld()
        result.exitCode == 0 || removed
    }

    override suspend fun apkUpgrade(onProgress: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        val TAG = "SandboxUpgrade"
        if (!isAvailable()) { Log.e(TAG, "not available"); return@withContext 0 }
        lastError = null
        ensurePackageMetadata()

        // 1. Download + parse APKINDEX
        onProgress("Fetching package index...")
        val indexUrl = "$alpineMirror/aarch64/APKINDEX.tar.gz"
        Log.e(TAG, "fetch index: $indexUrl")
        val indexFile = File(context.filesDir, "APKINDEX_UPGRADE.tar.gz")
        try {
            val conn = openHttp(indexUrl)
            Log.e(TAG, "index responseCode=${conn.responseCode}")
            if (conn.responseCode != 200) { onProgress("HTTP ${conn.responseCode}"); lastError = "HTTP ${conn.responseCode} from $indexUrl"; return@withContext 0 }
            conn.inputStream.use { i -> indexFile.outputStream().use { o -> i.copyTo(o) } }
            Log.e(TAG, "index downloaded bytes=${indexFile.length()}")
        } catch (e: Throwable) { Log.e(TAG, "index download FAIL", e); onProgress("FAIL: ${e.message}"); lastError = e.message; return@withContext 0 }

        val repoPkgs: Map<String, FullPkgEntry>
        val soToPkg: Map<String, String>
        try {
            val (r, s) = parseFullApkIndex(indexFile)
            repoPkgs = r; soToPkg = s
        } catch (e: Throwable) {
            onProgress("FAIL: parse index — ${e.javaClass.simpleName}: ${e.message}"); lastError = "Parse index: ${e.message}"; indexFile.delete(); return@withContext 0
        } finally { indexFile.delete() }

        // 2. Read installed DB
        val installed = readInstalledVersions()

        // 3. Collect installed packages where repo has a newer version
        val toUpgrade = linkedSetOf<String>()
        for ((name, instVer) in installed) {
            val repoEntry = repoPkgs[name] ?: continue
            if (compareAlpineVersions(repoEntry.version, instVer) > 0) toUpgrade.add(name)
        }
        if (toUpgrade.isEmpty()) { Log.e(TAG, "nothing to upgrade (${installed.size} installed pkgs)"); onProgress("All packages up to date."); return@withContext 0 }
        Log.e(TAG, "${toUpgrade.size} pkgs to upgrade: ${toUpgrade.joinToString()}")

        // 4. Recursively add transitive deps of upgradable packages.
        // IMPORTANT: when a dependency is a virtual "provides" name (e.g. cmd:sh) resolved
        // via soToPkg, only follow the provider that is ALREADY installed. Following any
        // other provider would pull in a conflicting package (e.g. both yash-binsh and
        // busybox-binsh provide /bin/sh — installing both deadlocks apk).
        val visited = mutableSetOf<String>()
        val toInstall = linkedSetOf<String>()
        fun collect(name: String) {
            if (name in visited || name !in repoPkgs) return
            visited.add(name)
            val instVer = installed[name]
            if (instVer == null || compareAlpineVersions(repoPkgs[name]!!.version, instVer) > 0) toInstall.add(name)
            for (dep in repoPkgs[name]!!.deps) {
                val dn = dep.takeWhile { it != '=' && it != '>' && it != '<' && it != '~' }
                if (dn.isNotEmpty()) {
                    when {
                        dn in repoPkgs -> collect(dn)
                        else -> {
                            // Virtual/provides name: only follow if the resolved provider is
                            // already installed — never introduce a new alternative provider.
                            val provider = soToPkg[dn]
                            if (provider != null && provider in installed) collect(provider)
                        }
                    }
                }
            }
        }
        for (name in toUpgrade) collect(name)
        onProgress("${toInstall.size} packages to upgrade")

        // 5. Download + install (same pattern as apkInstall)
        val tmpDir = File(rootfsDir, "tmp"); tmpDir.listFiles()?.forEach { it.delete() }; tmpDir.mkdirs()
        val paths = mutableListOf<String>()
        for (name in toInstall) {
            val ver = repoPkgs[name]?.version ?: continue
            val fn = "$name-$ver.apk"; val f = File(context.filesDir, fn)
            if (!f.exists() || f.length() == 0L) {
                onProgress("Downloading $fn...")
                try {
                    val conn = openHttp("$alpineMirror/aarch64/$fn")
                    if (conn.responseCode != 200) {
                        onProgress("HTTP ${conn.responseCode}")
                        lastError = "HTTP ${conn.responseCode}: $fn"
                        tmpDir.listFiles()?.forEach { it.delete() }
                        return@withContext 0
                    }
                    conn.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                } catch (ex: Throwable) { onProgress("FAIL: ${ex.message}"); lastError = "Download: ${ex.message}"; tmpDir.listFiles()?.forEach { it.delete() }; return@withContext 0 }
            }
            val dst = File(tmpDir, fn); f.copyTo(dst, true); f.delete(); paths.add("/tmp/$fn")
        }

        onProgress("Installing ${paths.size} packages...")
        Log.e(TAG, "executing apk add for ${paths.size} pkgs: ${paths.joinToString()}")
        val result = executeRaw("apk add --allow-untrusted --no-network ${paths.joinToString(" ") { shellQuote(it) }}", timeoutMs = 300000)
        Log.e(TAG, "apk add exit=${result.exitCode}")
        Log.e(TAG, "apk add stdout: ${result.stdout.take(2000)}")
        Log.e(TAG, "apk add stderr: ${result.stderr.take(2000)}")
        onProgress(result.stdout); tmpDir.listFiles()?.forEach { it.delete() }
        normalizeWorld()
        val after = readInstalledVersions()
        val upgradedCount = toUpgrade.count { name ->
            val beforeVersion = installed[name]
            val afterVersion = after[name]
            beforeVersion != null && afterVersion != null && compareAlpineVersions(afterVersion, beforeVersion) > 0
        }
        if (result.exitCode != 0 && upgradedCount == 0) {
            lastError = result.stderr.ifBlank { result.stdout }.ifBlank { "Upgrade failed" }
            return@withContext 0
        }
        upgradedCount
    }

    override suspend fun getDiskUsageMB(): Long = withContext(Dispatchers.IO) {
        try { rootfsDir.walkTopDown().sumOf { it.length() } / (1024 * 1024) } catch (_: Throwable) { 0L }
    }

    // ── Tar Extraction ──────────────────────────────────

    private fun extractTarEntries(tar: org.apache.commons.compress.archivers.tar.TarArchiveInputStream, destDir: File) {
        val destPrefix = destDir.canonicalPath + File.separator
        // Reject any entry whose resolved path escapes destDir (Zip-Slip / path traversal).
        fun safeChild(name: String): File? {
            val f = File(destDir, name)
            return if (f.canonicalPath == destDir.canonicalPath || f.canonicalPath.startsWith(destPrefix)) f else null
        }
        val symlinks = mutableListOf<Pair<String, String>>()
        var entry = tar.nextEntry
        while (entry != null) {
            val outFile = safeChild(entry.name)
            if (outFile == null) { entry = tar.nextEntry; continue }
            when {
                entry.isDirectory -> outFile.mkdirs()
                entry.isSymbolicLink -> { outFile.parentFile?.mkdirs(); symlinks.add(entry.name to entry.linkName) }
                entry.isFile -> { outFile.parentFile?.mkdirs(); outFile.outputStream().use { tar.copyTo(it) }; if (entry.mode and 0x40 != 0) outFile.setExecutable(true, false) }
            }
            entry = tar.nextEntry
        }
        for ((name, target) in symlinks) {
            val outFile = safeChild(name) ?: continue; if (outFile.exists()) continue
            val src = if (target.startsWith("/")) File(destDir, target.trimStart('/'))
                      else File(outFile.parentFile ?: destDir, target)
            // Containment check on the symlink source too.
            if (src.canonicalPath != destDir.canonicalPath && !src.canonicalPath.startsWith(destPrefix)) continue
            if (!src.exists()) continue
            try {
                if (src.isDirectory) src.walkTopDown().forEach { f -> val rel = f.relativeTo(src).path; val dst = File(outFile, rel); if (f.isDirectory) dst.mkdirs() else { dst.parentFile?.mkdirs(); f.copyTo(dst, true) } }
                else { outFile.parentFile?.mkdirs(); src.copyTo(outFile, true) }
            } catch (_: Throwable) {}
        }
    }

    // ── APKINDEX Parsing ────────────────────────────────

    private data class FullPkgEntry(val name: String, val version: String, val deps: List<String>)

    /** Compare two Alpine-style package versions. Returns >0 if a > b, 0 if equal, <0 if a < b.
     *  Alpine version format: {version}-r{revision}  (e.g. "3.5.2-r1", "1.2.3_pre1-r0").
     *  -r{revision} is the package revision; if omitted, revision=0.
     *  The version part is split into tokens: digit runs vs non-digit runs.
     *  Tokens are compared numerically for digits, lexicographically for letters.
     *  '_' (underscore) acts as a separator with lower priority than '.'. */
    private fun compareAlpineVersions(a: String, b: String): Int {
        fun splitVersion(v: String): Pair<String, Int> {
            val ri = v.lastIndexOf("-r")
            val base = if (ri >= 0) v.substring(0, ri) else v
            val rev  = if (ri >= 0) v.substring(ri + 2).toIntOrNull() ?: 0 else 0
            return base to rev
        }
        fun tokenise(ver: String): List<String> {
            val tokens = mutableListOf<String>()
            var i = 0
            while (i < ver.length) {
                if (ver[i] == '.' || ver[i] == '_' || ver[i] == '-') {
                    tokens.add(ver[i].toString()); i++
                } else if (ver[i].isDigit()) {
                    val start = i; while (i < ver.length && ver[i].isDigit()) i++
                    tokens.add(ver.substring(start, i))
                } else {
                    val start = i; while (i < ver.length && !ver[i].isDigit() && ver[i] != '.' && ver[i] != '_' && ver[i] != '-') i++
                    tokens.add(ver.substring(start, i))
                }
            }
            return tokens
        }
        fun tokenWeight(token: String): Int = when {
            token == "~" -> -1
            token.startsWith("alpha") -> -4
            token.startsWith("beta")  -> -3
            token.startsWith("pre")   -> -2
            token.startsWith("rc")    -> -1
            else -> 0
        }
        fun compareToken(ta: String, tb: String): Int? {
            val aDig = ta.toIntOrNull()
            val bDig = tb.toIntOrNull()
            if (aDig != null && bDig != null) return aDig.compareTo(bDig)
            // letter tokens: compare pre-release suffixes first, then lexicographically
            val wa = tokenWeight(ta); val wb = tokenWeight(tb)
            if (wa != 0 || wb != 0) return wa.compareTo(wb)
            return ta.compareTo(tb)
        }

        val (baseA, revA) = splitVersion(a)
        val (baseB, revB) = splitVersion(b)

        val tokensA = tokenise(baseA)
        val tokensB = tokenise(baseB)
        val n = maxOf(tokensA.size, tokensB.size)
        for (idx in 0 until n) {
            val ta = tokensA.getOrElse(idx) { "" }
            val tb = tokensB.getOrElse(idx) { "" }
            if (ta == "_" && tb == "_") continue
            if (ta == "_") return -1   // _ has lower priority than anything except another _
            if (tb == "_") return 1
            if (ta == tb) continue
            val cmp = compareToken(ta, tb) ?: ta.compareTo(tb)
            if (cmp != 0) return cmp
        }
        return revA.compareTo(revB)
    }

    private fun parseFullApkIndex(indexFile: File): Pair<Map<String, FullPkgEntry>, Map<String, String>> {
        val result = mutableMapOf<String, FullPkgEntry>()
        val soToPkg = mutableMapOf<String, String>()
        java.util.zip.GZIPInputStream(indexFile.inputStream()).use { gz ->
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (entry.name == "APKINDEX") {
                        val lines = tar.readBytes().toString(Charsets.UTF_8).lines()
                        for (i in lines.indices) {
                            val line = lines[i].trim()
                            if (!line.startsWith("P:")) continue
                            val name = line.substring(2).trim()
                            var version = ""; var provider = ""
                            val deps = mutableListOf<String>()
                            val isSoEntry = name.startsWith("so:")
                            for (j in i + 1 until minOf(i + 30, lines.size)) {
                                val n = lines[j].trim()
                                if (n.startsWith("C:")) break
                                if (n.startsWith("V:")) version = n.substring(2).trim()
                                if (n.startsWith("p:")) {
                                    provider = n.substring(2).trim()
                                    if (!isSoEntry) {
                                        // p: lists all provides (so:libfoo.so.1=1.0 so:libbar.so.1=1.0)
                                        for (prov in provider.split(Regex("\\s+"))) {
                                            val pn = prov.takeWhile { it != '=' }
                                            if (pn.isNotEmpty()) soToPkg[pn] = name
                                        }
                                    }
                                }
                                if (n.startsWith("D:")) deps.addAll(n.substring(2).trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
                            }
                            if (isSoEntry && provider.isNotEmpty()) soToPkg[name] = provider
                            else if (!isSoEntry && name.isNotEmpty() && version.isNotEmpty()) result[name] = FullPkgEntry(name, version, deps)
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
        return Pair(result, soToPkg)
    }

    // ── Helpers ────────────────────────────────────────

    private fun sanitizePackageName(packageName: String): String {
        val trimmed = packageName.trim()
        require(packageNameRegex.matches(trimmed)) { "Invalid package name: $packageName" }
        return trimmed
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun installedDbFile(): File = File(rootfsDir, "lib/apk/db/installed")

    private fun readInstalledVersions(): LinkedHashMap<String, String> {
        val installed = linkedMapOf<String, String>()
        val db = installedDbFile()
        if (!db.exists()) return installed
        var name = ""
        var version = ""
        db.readLines(Charsets.UTF_8).forEach { line ->
            when {
                line.startsWith("P:") -> name = line.substring(2).trim()
                line.startsWith("V:") -> version = line.substring(2).trim()
                line.isBlank() -> {
                    if (name.isNotEmpty()) installed[name] = version
                    name = ""
                    version = ""
                }
            }
        }
        if (name.isNotEmpty()) installed[name] = version
        return installed
    }

    private fun worldFile(): File = File(rootfsDir, "etc/apk/world")

    private fun readWorldLines(): LinkedHashSet<String> {
        val world = worldFile()
        if (!world.exists()) return linkedSetOf()
        return world.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
    }

    private fun writeWorldLines(lines: Collection<String>) {
        val world = worldFile()
        world.parentFile?.mkdirs()
        world.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"), Charsets.UTF_8)
    }

    private fun worldPackageName(line: String): String {
        val cleaned = line.trim().removePrefix("!")
        val end = cleaned.indexOfFirst { it == '@' || it == '<' || it == '>' || it == '=' || it == '~' }
        return if (end >= 0) cleaned.substring(0, end) else cleaned
    }

    private fun captureBaseWorld(force: Boolean = false) {
        metadataDir.mkdirs()
        if (!force && baseWorldFile.exists()) return
        val current = readWorldLines()
        val installed = readInstalledVersions().keys
        val inferredBase = current.filter { worldPackageName(it) in defaultBaseWorld && worldPackageName(it) in installed }
        val fallbackBase = defaultBaseWorld.filter { it in installed }
        val base = when {
            force && current.isNotEmpty() -> current
            force -> fallbackBase.ifEmpty { defaultBaseWorld }
            inferredBase.isNotEmpty() -> inferredBase
            else -> current
        }
        baseWorldFile.writeText(base.joinToString("\n", postfix = if (base.isEmpty()) "" else "\n"), Charsets.UTF_8)
    }

    private fun readBaseWorld(): LinkedHashSet<String> {
        captureBaseWorld()
        return baseWorldFile.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
    }

    private fun readExplicitPackages(): LinkedHashSet<String> {
        if (!explicitPackagesFile.exists()) return linkedSetOf()
        return explicitPackagesFile.readLines(Charsets.UTF_8)
            .mapNotNull { runCatching { sanitizePackageName(it) }.getOrNull() }
            .toCollection(linkedSetOf())
    }

    private fun writeExplicitPackages(packages: Collection<String>) {
        metadataDir.mkdirs()
        val clean = packages.mapNotNull { runCatching { sanitizePackageName(it) }.getOrNull() }.toCollection(linkedSetOf())
        explicitPackagesFile.writeText(clean.joinToString("\n", postfix = if (clean.isEmpty()) "" else "\n"), Charsets.UTF_8)
    }

    private fun ensurePackageMetadata() {
        if (!rootfsDir.isDirectory) return
        metadataDir.mkdirs()
        captureBaseWorld()
        if (!explicitPackagesFile.exists()) {
            val baseNames = readBaseWorld().map { worldPackageName(it) }.toSet()
            val migratedExplicit = readWorldLines()
                .map { worldPackageName(it) }
                .filter { it !in baseNames }
                .mapNotNull { runCatching { sanitizePackageName(it) }.getOrNull() }
                .toCollection(linkedSetOf())
            writeExplicitPackages(migratedExplicit)
        }
        normalizeWorld()
    }

    private fun normalizeWorld(explicitPackages: Set<String> = readExplicitPackages()) {
        metadataDir.mkdirs()
        val base = readBaseWorld()
        val baseNames = base.map { worldPackageName(it) }.toSet()
        val normalized = linkedSetOf<String>()
        normalized.addAll(base)
        explicitPackages
            .mapNotNull { runCatching { sanitizePackageName(it) }.getOrNull() }
            .filter { it !in baseNames }
            .forEach { normalized.add(it) }
        writeWorldLines(normalized)
    }

    private fun addExplicitPackage(packageName: String) {
        val name = sanitizePackageName(packageName)
        ensurePackageMetadata()
        val next = readExplicitPackages().apply { add(name) }
        writeExplicitPackages(next)
        normalizeWorld(next)
    }

    private data class ResolvedSandboxPath(
        val file: File,
        val physicalRoot: File,
        val virtualRoot: String
    )

    private fun ensureSandboxMountTargets() {
        homeMountDir.mkdirs()
        File(rootfsDir, homeMountPath.trimStart('/')).mkdirs()
    }

    private fun normalizeVirtualPath(path: String): String {
        val raw = path.trim().replace('\\', '/')
        val absolute = if (raw.isBlank()) "/" else if (raw.startsWith("/")) raw else "/$raw"
        val collapsed = absolute.replace(Regex("/+"), "/")
        return if (collapsed.length > 1) collapsed.trimEnd('/') else collapsed
    }

    private fun resolveSandboxPath(path: String): ResolvedSandboxPath {
        val normalized = normalizeVirtualPath(path)
        if (normalized == homeMountPath || normalized.startsWith("$homeMountPath/")) {
            ensureSandboxMountTargets()
            val root = homeMountDir.canonicalFile
            val suffix = normalized.removePrefix(homeMountPath).trimStart('/')
            val resolved = File(root, suffix).canonicalFile
            require(resolved.absolutePath == root.absolutePath || resolved.absolutePath.startsWith(root.absolutePath + File.separator)) {
                "Path traversal: $path"
            }
            return ResolvedSandboxPath(resolved, root, homeMountPath)
        }

        val root = rootfsDir.canonicalFile
        val resolved = File(root, normalized.trimStart('/')).canonicalFile
        require(resolved.absolutePath == root.absolutePath || resolved.absolutePath.startsWith(root.absolutePath + File.separator)) {
            "Path traversal: $path"
        }
        return ResolvedSandboxPath(resolved, root, "/")
    }

    private fun resolvePath(path: String): File = resolveSandboxPath(path).file

    // remaining: levels still allowed including the current dir's files. -1 = unlimited;
    // 1 = only this dir's files (no descent); >1 = descend with one fewer level.
    private fun walkVirtualFiles(
        dir: File,
        result: MutableList<String>,
        physicalRootAbsPath: String,
        virtualRoot: String,
        remaining: Int = -1
    ) {
        try { dir.listFiles()?.forEach {
            if (it.isDirectory) {
                if (remaining < 0 || remaining > 1) {
                    walkVirtualFiles(it, result, physicalRootAbsPath, virtualRoot, if (remaining < 0) -1 else remaining - 1)
                }
            } else {
                val path = try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
                val rel = path.removePrefix(physicalRootAbsPath).removePrefix(File.separator).replace(File.separatorChar, '/')
                val prefix = if (virtualRoot == "/") "" else virtualRoot.trimEnd('/')
                result.add("$prefix/$rel")
            }
        } } catch (_: Throwable) {}
    }

    private fun globMatch(files: List<String>, pattern: String): List<String> {
        val cleanPattern = pattern.trim().replace('\\', '/')
        val adjusted = when {
            cleanPattern.isBlank() -> "/**"
            cleanPattern.startsWith("/") -> cleanPattern
            cleanPattern.contains("/") -> "/$cleanPattern"
            else -> "/**/$cleanPattern"
        }
        val matcher = try { FileSystems.getDefault().getPathMatcher("glob:$adjusted") } catch (_: Throwable) { return emptyList() }
        return files.filter { f -> try { matcher.matches(java.nio.file.Paths.get(f)) } catch (_: Throwable) { false } }
    }
}
