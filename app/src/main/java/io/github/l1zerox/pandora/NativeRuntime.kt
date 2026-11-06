package io.github.l1zerox.pandora

import android.content.Context
import java.io.File

internal object NativeRuntime {
    fun command(context: Context, binary: String, args: List<String>): ProcessBuilder {
        val packages = File(context.noBackupFilesDir,"youtubedl-android/packages")
        val nativeDir = context.applicationInfo.nativeLibraryDir
        return ProcessBuilder(listOf(File(nativeDir,binary).absolutePath) + args).redirectErrorStream(true).apply {
            // FFmpeg also links libraries shipped in the Python package (crypto, libc++, etc.).
            environment()["LD_LIBRARY_PATH"] = listOf("python","ffmpeg").joinToString(":") {
                File(packages,"$it/usr/lib").absolutePath
            }
            environment()["TMPDIR"] = context.cacheDir.absolutePath
            environment()["HOME"] = context.filesDir.absolutePath
            environment()["PATH"] = System.getenv("PATH").orEmpty() + ":" + nativeDir
        }
    }
}
