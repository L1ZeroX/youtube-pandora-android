package io.github.l1zerox.pandora

import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Separate synchronous file: a terminating process cannot wait for the normal log executor. */
internal class CrashJournal(directory: File) {
    private val pending=File(directory,"pending-crash.txt")
    fun write(thread: Thread, error: Throwable, version: String, stage: String) {
        val causes=mutableListOf<Throwable>()
        val seen=Collections.newSetFromMap(IdentityHashMap<Throwable,Boolean>())
        var current: Throwable?=error
        while(current != null && causes.size < 8 && seen.add(current)) {
            causes += current
            current=current.cause
        }
        val text=buildString {
            appendLine("Youtube Pandora! $version / thread=${thread.name.take(120)}")
            appendLine("直前の処理: ${stage.take(300)}")
            appendLine("未処理の例外（実際に終了を起こした例外）:")
            // Put the cause chain first so a long stack cannot hide the original exception.
            causes.forEachIndexed { index, cause ->
                appendLine("原因[$index] ${cause.javaClass.name}: ${cause.message.orEmpty().take(1000)}")
            }
            causes.forEachIndexed { index, cause ->
                appendLine("スタック[$index] ${cause.javaClass.name}")
                cause.stackTrace.take(16).forEach { appendLine("  at $it") }
                if(cause.stackTrace.size > 16) appendLine("  …残り ${cause.stackTrace.size-16} フレーム省略")
                cause.suppressed.take(3).forEach { appendLine("  suppressed: ${it.javaClass.name}: ${it.message.orEmpty().take(250)}") }
            }
        }
        pending.parentFile!!.mkdirs()
        val bytes=cleanLog(text,16_000,keepStart=true).toByteArray(Charsets.UTF_8)
        FileOutputStream(pending).use { it.write(bytes); it.fd.sync() }
    }
    fun read(): LogEntry? {
        if(!pending.exists()) return null
        require(pending.length() <= 64*1024) { "クラッシュ記録が上限を超えています。" }
        return LogEntry(pending.lastModified(),"ERROR","クラッシュ詳細",pending.readText(Charsets.UTF_8))
    }
    fun clear() { check(!pending.exists() || pending.delete()) { "クラッシュ記録を整理できません。" } }
}

internal class RecordingCrashHandler(
    private val write: (Thread,Throwable)->Unit,
    private val delegate: Thread.UncaughtExceptionHandler
): Thread.UncaughtExceptionHandler {
    private val recorded=AtomicBoolean(false)
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if(recorded.compareAndSet(false,true)) write(thread,error)
        } catch(_: Throwable) {
            // Never replace the original exception with a logging failure.
        } finally { delegate.uncaughtException(thread,error) }
    }
}
