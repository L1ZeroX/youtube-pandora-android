package io.github.l1zerox.pandora

import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

internal const val BUNDLED_EXTRACTOR_VERSION = "2026.10.26"
internal const val BUNDLED_EXTRACTOR_SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"

internal fun extractorVersion(file: File): String? = runCatching {
    file.inputStream().use(::extractorVersion)
}.getOrNull()

/** A zipapp starts with a Python shebang, which Android's random-access ZIP reader may reject. */
internal fun extractorVersion(stream: InputStream): String? = runCatching {
    stream.buffered().use { input ->
        input.mark(4)
        val first=input.read(); val second=input.read()
        if(first == '#'.code && second == '!'.code) {
            var length=2
            while(true) {
                val next=input.read()
                if(next < 0 || ++length > 4096) return@use null
                if(next == '\n'.code) break
            }
        } else input.reset()
        input.mark(4)
        if(input.read() != 0x50 || input.read() != 0x4b || input.read() != 3 || input.read() != 4) return@use null
        input.reset()
        ZipInputStream(input).use zip@ { zip ->
            val buffer=ByteArray(8192)
            var total=0L
            repeat(4096) {
                val entry=zip.nextEntry ?: return@zip null
                val target=entry.name == "yt_dlp/version.py"
                val contents=if(target) ByteArrayOutputStream() else null
                while(true) {
                    val count=zip.read(buffer)
                    if(count < 0) break
                    total+=count
                    if(total > 16*1024*1024 || (target && contents!!.size()+count > 8192)) return@zip null
                    contents?.write(buffer,0,count)
                }
                if(target) return@zip Regex("__version__\\s*=\\s*['\"]([^'\"]+)['\"]")
                    .find(contents!!.toString("UTF-8"))?.groupValues?.get(1)
            }
            null
        }
    }
}.getOrNull()

/** Integrity verification does not depend on ZIP parsing or platform-specific ZIP behavior. */
internal fun isBundledExtractor(file: File, check: ()->Unit = {}): Boolean {
    val digest=MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer=ByteArray(65536)
        while(true) { check(); val count=input.read(buffer); if(count < 0) break; digest.update(buffer,0,count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) } == BUNDLED_EXTRACTOR_SHA256
}

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
