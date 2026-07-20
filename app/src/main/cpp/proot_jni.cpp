#include <jni.h>

/**
 * Minimal JNI stub for System.loadLibrary("orangeisland_proot").
 *
 * The actual proot execution uses ProcessBuilder on libproot_exec.so
 * (built via GNUmakefile, see .build-proot/). This library exists only
 * so that System.loadLibrary succeeds and triggers APK native lib extraction.
 *
 * ProotNative.execute() is never called — ProotSandboxManager uses
 * ProcessBuilder directly.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_orangeisland_app_sandbox_ProotNative_execute(
    JNIEnv *, jclass, jobjectArray) {
    return -1; // Stub: actual execution goes through ProcessBuilder
}
