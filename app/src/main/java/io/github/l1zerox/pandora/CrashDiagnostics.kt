package io.github.l1zerox.pandora

import android.content.Context
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

internal object CrashDiagnostics {
    @Volatile var stage="アプリ起動"
    fun install(context: Context) {
        val crash=CrashJournal(File(context.filesDir,"diagnostics"))
        val previous=Thread.getDefaultUncaughtExceptionHandler() ?: Thread.UncaughtExceptionHandler { _, _ ->
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
        Thread.setDefaultUncaughtExceptionHandler(RecordingCrashHandler(
            { thread, error -> crash.write(thread,error,BuildConfig.VERSION_NAME,stage) },previous))
    }
}
