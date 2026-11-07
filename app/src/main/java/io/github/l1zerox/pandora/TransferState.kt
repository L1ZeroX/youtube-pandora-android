package io.github.l1zerox.pandora

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException

internal enum class StopReason { NONE, PAUSE, CANCEL }

/** Cancellation wins if a pause and a cancel arrive before the worker has stopped. */
internal class TransferControl {
    private val reason = AtomicReference(StopReason.NONE)
    val paused get() = reason.get() == StopReason.PAUSE
    val stopped get() = reason.get() != StopReason.NONE
    fun pause() { reason.compareAndSet(StopReason.NONE,StopReason.PAUSE) }
    fun cancel() { reason.set(StopReason.CANCEL) }
    fun check() { if(stopped) throw CancellationException(if(paused) "一時停止" else "キャンセル") }
}

internal fun transferWorkspace(root: File, video: Video, p: Preferences): File {
    val identity=(listOf(video.url,p.format,p.quality,p.audio,p.fps) +
        (if(p.preferJapaneseAudio) listOf("prefer-japanese-audio-v1") else emptyList()) +
        if(p.format == "MP3") listOf(p.artworkMode,p.artworkImage) else emptyList()).joinToString("\n")
    val hash=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(root,"transfer-$hash")
}

internal class DownloadBatch(val preferences: Preferences, videos: List<Video>, retry: Boolean) {
    private val initialKeys=videos.map { it.key }.toSet()
    private val retryKeys=if(retry) videos.filter { it.status == "失敗" }.map { it.key }.toSet() else null
    val attempted=mutableSetOf<String>()
    var failed=0
    @Volatile var workspace: File? = null
    fun remaining(videos: List<Video>) = videos.filter {
        it.key !in attempted && it.status != "完了" &&
            (if(retryKeys == null) it.selected else it.key in retryKeys || (it.key !in initialKeys && it.selected))
    }
}

/** Only confirmed, published downloads leave the queue; media files are not touched. */
internal fun ScreenState.withoutSavedDownloads(): ScreenState {
    val completed=videos.filter { it.status == "完了" && it.savedUri.isNotBlank() }.map { it.key }.toSet()
    if(completed.isEmpty()) return this
    return copy(videos=videos.filterNot { it.key in completed },current=if(current in completed) "" else current)
}

internal data class TransferProgress(val fraction: Float?, val speed: String, val eta: Double?, val detail: String)
private fun number(value: String) = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
internal fun byteText(bytes: Double): String {
    val units=listOf("B","KiB","MiB","GiB","TiB")
    var value=bytes; var unit=0
    while(value >= 1024 && unit < units.lastIndex) { value/=1024; unit++ }
    return String.format(Locale.US,"%.1f %s",value,units[unit])
}

internal fun transferProgress(line: String): TransferProgress? {
    if(line.startsWith("PANDORA_PROGRESS|")) {
        val fields=line.split('|')
        if(fields.size != 7) return null
        val downloaded=number(fields[2]); val total=number(fields[3])?.takeIf { it > 0 }
            ?: number(fields[4])?.takeIf { it > 0 }
        val speed=number(fields[5])?.let { byteText(it)+"/s" }.orEmpty()
        val eta=number(fields[6])
        val fraction=if(fields[1] == "finished") 1f else if(downloaded != null && total != null)
            (downloaded/total).toFloat().coerceIn(0f,1f) else null
        val size=downloaded?.let { byteText(it) }.orEmpty() + (total?.let { " / ${byteText(it)}" } ?: " / 容量不明")
        return TransferProgress(fraction,speed,eta,"$size · 速度 ${speed.ifBlank { "計測中" }} · 残り ${eta?.let(::durationText) ?: "不明"}")
    }
    if(line.startsWith("PANDORA_CONVERT|")) {
        val fields=line.split('|'); if(fields.size != 4) return null
        val position=number(fields[1]) ?: return null
        val duration=number(fields[2])?.takeIf { it > 0 }
        val elapsed=number(fields[3])?.takeIf { it > 0 }
        val rate=elapsed?.let { position/it }?.takeIf { it > 0 }
        val eta=if(duration != null && rate != null) ((duration-position).coerceAtLeast(0.0)/rate) else null
        val speed=rate?.let { String.format(Locale.US,"%.2fx",it) }.orEmpty()
        return TransferProgress(duration?.let { (position/it).toFloat().coerceIn(0f,.999f) },speed,eta,
            "変換速度 ${speed.ifBlank { "計測中" }} · 残り ${eta?.let(::durationText) ?: "不明"}")
    }
    return null
}

internal fun sharedUrls(text: String): List<String> = Regex("https?://[^\\s<>\"\\u3000]+",RegexOption.IGNORE_CASE)
    .findAll(text).map { it.value.trimEnd(')',']','}', '）','」','』','。','、') }
    .filter { runCatching { validUrls(it); true }.getOrDefault(false) }.distinctBy(::mediaIdentity).take(500).toList()

internal fun mediaIdentity(url: String): String {
    val uri=runCatching { URI(url) }.getOrNull() ?: return url
    val host=uri.host?.lowercase().orEmpty()
    val id=when {
        host == "youtu.be" -> uri.path.trim('/').substringBefore('/')
        host == "youtube.com" || host.endsWith(".youtube.com") -> {
            if(uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/")) uri.path.split('/').getOrNull(2)
            else uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
                ?.let { runCatching { URLDecoder.decode(it,"UTF-8") }.getOrDefault(it) }
        }
        else -> null
    }
    return if(!id.isNullOrBlank()) "youtube:$id" else url.substringBefore('#')
}

internal fun appendSharedVideos(videos: List<Video>, text: String): List<Video> {
    val identities=videos.map { mediaIdentity(it.url) }.toMutableSet()
    val added=sharedUrls(text).filter { identities.add(mediaIdentity(it)) }.map { url ->
        Video("shared:${mediaIdentity(url)}",url,url,site=URI(url).host.orEmpty(),thumbnail=youtubeThumbnail(url),resolved=false)
    }
    return videos+added
}
