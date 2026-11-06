package io.github.l1zerox.pandora

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class LogSnapshot(val entries: List<LogEntry> = emptyList(), val storageWarning: String = "") {
    val errors get() = entries.count { it.level == "ERROR" }
}

object AppLog {
    val state = MutableStateFlow(LogSnapshot())
    private val worker = Executors.newSingleThreadExecutor()
    private var journal: LogJournal? = null
    private var lastProgress = 0L
    private var lastStage = ""
    fun init(context: Context) {
        val directory = File(context.filesDir,"diagnostics")
        worker.execute {
            runCatching {
                journal = LogJournal(directory)
                state.value = LogSnapshot(journal!!.read())
            }.onFailure { state.value = LogSnapshot(storageWarning="ログの読み込み失敗: ${cleanLog(it.toString())}") }
        }
        info("起動","Youtube Pandora! ${BuildConfig.VERSION_NAME} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / ${Build.MANUFACTURER} ${Build.MODEL} / ${Build.SUPPORTED_ABIS.joinToString()}")
    }
    fun info(stage: String, message: String) = record("INFO",stage,message)
    fun error(stage: String, error: Throwable) = record("ERROR",stage,error.stackTraceToString())
    @Synchronized fun progress(stage: String, detail: String) {
        val now = System.nanoTime()
        if(stage == lastStage && now-lastProgress < 5_000_000_000L) return
        lastStage=stage; lastProgress=now
        info(stage,detail)
    }
    private fun record(level: String, stage: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(),level,stage.replace(Regex("[\\t\\r\\n]")," ").take(50),cleanLog(message))
        worker.execute {
            var warning = state.value.storageWarning
            runCatching { journal?.append(entry) }.onFailure { warning="ログの保存失敗: ${cleanLog(it.toString())}" }
            // Bound both record count and text size so the UI and clipboard remain small.
            val entries = (state.value.entries + entry).takeLast(300).toMutableList()
            var size = entries.sumOf { it.message.length }
            while(size > 48_000 && entries.size > 1) size -= entries.removeAt(0).message.length
            state.value = LogSnapshot(entries,warning)
        }
    }
    fun clear() { worker.execute {
        runCatching { journal?.clear(); state.value=LogSnapshot() }
            .onFailure { state.value=state.value.copy(storageWarning="ログを消去できません: ${cleanLog(it.toString())}") }
    } }
    fun text(errorsOnly: Boolean = false): String {
        val snapshot = state.value
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US)
        return buildString {
            appendLine("Youtube Pandora! Android ${BuildConfig.VERSION_NAME} / エラー ${snapshot.errors} 件")
            appendLine("Android ${Build.VERSION.RELEASE} / ${Build.MANUFACTURER} ${Build.MODEL}")
            if(snapshot.storageWarning.isNotBlank()) appendLine(snapshot.storageWarning)
            snapshot.entries.filter { !errorsOnly || it.level == "ERROR" }.forEach {
                appendLine("${formatter.format(Date(it.time))} [${it.level}] ${it.stage}\n${it.message}")
            }
        }
    }
}
