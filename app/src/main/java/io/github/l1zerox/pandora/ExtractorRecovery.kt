package io.github.l1zerox.pandora

import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile

internal const val BUNDLED_EXTRACTOR_VERSION = "2026.10.26"

internal fun extractorVersion(file: File): String? = runCatching {
    ZipFile(file).use { zip ->
        val entry=zip.getEntry("yt_dlp/version.py") ?: return@use null
        zip.getInputStream(entry).bufferedReader().use { reader ->
            Regex("__version__\\s*=\\s*['\"]([^'\"]+)['\"]").find(reader.readText().take(8192))?.groupValues?.get(1)
        }
    }
}.getOrNull()

internal fun shouldInstallExtractor(installed: String?, bundled: String = BUNDLED_EXTRACTOR_VERSION): Boolean {
    fun date(value: String?) = value?.let { Regex("^(\\d{4})\\.(\\d{2})\\.(\\d{2})(?:\\D|$)").find(it) }
        ?.groupValues?.drop(1)?.take(3)?.joinToString("")?.toLongOrNull()
    val current=date(installed) ?: return true
    return current < requireNotNull(date(bundled))
}

internal fun youtubeForbidden(url: String, error: Throwable): Boolean =
    error !is CancellationException && youtubeVideoId(url) != null &&
        Regex("HTTP(?: Error| status(?: code)?)?[: ]+403\\b",RegexOption.IGNORE_CASE).containsMatchIn(error.message.orEmpty())

/** One recovery per item; a stop always wins over updating or retrying. */
internal fun <T> withYoutubeRecovery(url: String, check: ()->Unit, recover: ()->Unit, download: ()->T): T {
    check()
    try { return download() }
    catch(error: Exception) {
        check()
        if(!youtubeForbidden(url,error)) throw error
        recover(); check()
        return download()
    }
}

internal fun downloadFailureMessage(url: String, error: Throwable): String =
    if(youtubeForbidden(url,error)) "YouTubeに動画データの取得を拒否されました（403）。環境設定の「yt-dlpを更新」後に再試行してください。続く場合はログと動画URLを確認してください。\n${cleanLog(error.message.orEmpty(),1200)}"
    else (error.message ?: "保存できませんでした。").takeLast(2000)
