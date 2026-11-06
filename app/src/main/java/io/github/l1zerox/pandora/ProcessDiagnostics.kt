package io.github.l1zerox.pandora

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ProcessDiagnostics {
    fun reportPreviousExit(context: Context) {
        if(Build.VERSION.SDK_INT < 30) return
        runCatching {
            val preferences=context.getSharedPreferences("diagnostic-exits",Context.MODE_PRIVATE)
            val since=preferences.getLong("last_timestamp",System.currentTimeMillis()-24*60*60*1000L)
            val records=context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName,0,10)
                .filter { it.processName == context.packageName && it.timestamp > since }
                .sortedBy { it.timestamp }
            val format=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US)
            records.forEach { record ->
                val reason=when(record.reason) {
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "メモリー不足による終了"
                    ApplicationExitInfo.REASON_CRASH -> "アプリの例外による終了"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "ネイティブ処理のクラッシュ"
                    ApplicationExitInfo.REASON_ANR -> "応答なし（ANR）"
                    ApplicationExitInfo.REASON_SIGNALED -> "シグナルによる終了（原因は未確定）"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "ユーザー操作等による終了"
                    ApplicationExitInfo.REASON_USER_STOPPED -> "ユーザーによる停止"
                    ApplicationExitInfo.REASON_EXIT_SELF -> "プロセス自身による終了"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "リソース使用上限"
                    ApplicationExitInfo.REASON_OTHER -> "OSによるその他の終了"
                    else -> "その他・不明"
                }
                val description="${format.format(Date(record.timestamp))} / $reason / reason=${record.reason} status=${record.status} / PSS=${record.pss} KiB RSS=${record.rss} KiB / ${record.description.orEmpty()}"
                if(record.reason in setOf(ApplicationExitInfo.REASON_LOW_MEMORY,ApplicationExitInfo.REASON_CRASH,
                    ApplicationExitInfo.REASON_CRASH_NATIVE,ApplicationExitInfo.REASON_ANR,
                    ApplicationExitInfo.REASON_SIGNALED,ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE)) {
                    AppLog.error("Androidの終了記録",IllegalStateException(description))
                } else AppLog.info("Androidの終了記録",description)
            }
            records.lastOrNull()?.let { preferences.edit().putLong("last_timestamp",it.timestamp).apply() }
            if(records.isEmpty()) AppLog.info("Androidの終了記録","未記録の終了情報はありません（OSが理由を残さない場合もあります）。")
        }.onFailure { AppLog.info("Androidの終了記録","取得できません: ${it.message}") }
    }
    fun memory(context: Context, stage: String) {
        runCatching {
            val memory=ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            AppLog.info("メモリー", "$stage / 本体PSS=${Debug.getPss()} KiB / システム空き=${memory.availMem/(1024*1024)} MiB / lowMemory=${memory.lowMemory}")
        }
    }
}
