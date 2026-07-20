package com.orangeisland.app.sandbox

/**
 * JNI bridge to proot, compiled as liborangeisland_proot.so via CMake.
 * The .so is extracted by the system to nativeLibraryDir with exec permission,
 * bypassing the noexec restriction on app private directories.
 */
object ProotNative {
    init {
        System.loadLibrary("orangeisland_proot")
    }

    /**
     * Execute proot with the given arguments.
     * Forks internally — proot's main() calls exit() which would kill the process.
     * @return exit code from proot
     */
    external fun execute(args: Array<String>): Int
}
