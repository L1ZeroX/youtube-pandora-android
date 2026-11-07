package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class SharedPreview(val title: String, val thumbnail: String)

/** Preview-only fields: never replace download state, selection, or the full metadata flag. */
internal fun ScreenState.withSharedPreview(source: Video, preview: SharedPreview): ScreenState = copy(
    videos=videos.map { current ->
        if(current.key != source.key || current.url != source.url || current.resolved) current
        else current.copy(title=preview.title.ifBlank { current.title },
            thumbnail=preview.thumbnail.ifBlank { current.thumbnail },site="YouTube")
    })

/** Owned by the bubble service, not the panel or the short-lived share activity. */
internal class SharedPreviewLoader(
    private val scope: CoroutineScope,
    private val fetch: suspend (Video)->SharedPreview,
    private val apply: (Video,SharedPreview)->Unit,
    private val failed: (Throwable)->Unit = {},
    private val retryDelay: Long = 1500L,
) {
    private val jobs=mutableMapOf<String,Job>()
    private val slots=Semaphore(2)
    fun update(videos: List<Video>) {
        val candidates=videos.filter { !it.resolved && it.title == it.url && youtubeVideoId(it.url) != null }
        val keys=candidates.map { it.key }.toSet()
        jobs.keys.filter { it !in keys }.forEach { jobs.remove(it)?.cancel() }
        for(video in candidates.asReversed()) {
            if(video.key in jobs) continue
            val job=scope.launch(start=CoroutineStart.LAZY) {
                repeat(3) { attempt ->
                    try {
                        val preview=slots.withPermit { fetch(video) }
                        ensureActive()
                        apply(video,preview)
                        return@launch
                    } catch(e: CancellationException) { throw e }
                    catch(e: Exception) {
                        if(attempt == 2) failed(e) else delay(retryDelay*(attempt+1))
                    }
                }
            }
            jobs[video.key]=job
            job.start()
        }
    }
}

/** HTTP preview lookup cannot interrupt the native download/conversion process. */
internal suspend fun fetchSharedPreview(video: Video): SharedPreview = withContext(Dispatchers.IO) {
    val id=requireNotNull(youtubeVideoId(video.url))
    val watch=URLEncoder.encode("https://www.youtube.com/watch?v=$id","UTF-8")
    val connection=URL("https://www.youtube.com/oembed?url=$watch&format=json").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=10000; connection.readTimeout=10000
        connection.setRequestProperty("Accept","application/json")
        check(connection.responseCode == 200) { "YouTubeのプレビュー情報を取得できませんでした (${connection.responseCode})" }
        val text=connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer=CharArray(65537)
            var count=0
            while(count < buffer.size) {
                val read=reader.read(buffer,count,buffer.size-count)
                if(read < 0) break
                count+=read
            }
            check(count <= 65536) { "プレビュー情報が大きすぎます。" }
            String(buffer,0,count)
        }
        val json=JSONObject(text)
        val title=json.optString("title").trim()
        check(title.isNotEmpty()) { "動画タイトルを取得できませんでした。" }
        val thumbnail=json.optString("thumbnail_url").takeIf { it.startsWith("https://") }.orEmpty()
        SharedPreview(title.take(1000),thumbnail)
    } finally { connection.disconnect() }
}
