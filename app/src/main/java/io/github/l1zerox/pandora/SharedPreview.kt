package io.github.l1zerox.pandora

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class SharedPreview(val title: String, val thumbnail: String, val duration: Double? = null)

/** Preview-only fields: never replace download state, selection, or the full metadata flag. */
internal fun ScreenState.withSharedPreview(source: Video, preview: SharedPreview): ScreenState = copy(
    videos=videos.map { current ->
        if(current.key != source.key || current.url != source.url) current
        else if(current.resolved) current.copy(duration=positiveDuration(current.duration) ?: positiveDuration(preview.duration))
        else current.copy(title=preview.title.ifBlank { current.title },
            thumbnail=preview.thumbnail.ifBlank { current.thumbnail },site="YouTube",
            duration=positiveDuration(current.duration) ?: positiveDuration(preview.duration))
    })

/** Shared by the main screen and bubble; network lookups never invoke the native downloader. */
internal class SharedPreviewLoader(
    private val scope: CoroutineScope,
    private val fetch: suspend (Video)->SharedPreview,
    private val apply: (Video,SharedPreview)->Unit,
    private val failed: (Throwable)->Unit = {},
    private val retryDelay: Long = 1500L,
    private val fetchDuration: (suspend (Video)->Double?)? = null,
) {
    private val jobs=mutableMapOf<String,Job>()
    private val slots=Semaphore(2)
    fun update(videos: List<Video>) {
        val candidates=videos.filter { youtubeVideoId(it.url) != null &&
            ((!it.resolved && it.title == it.url) || (fetchDuration != null && positiveDuration(it.duration) == null)) }
        val keys=candidates.map { it.key }.toSet()
        jobs.keys.filter { it !in keys }.forEach { jobs.remove(it)?.cancel() }
        for(video in candidates.asReversed()) {
            if(video.key in jobs) continue
            val job=scope.launch(start=CoroutineStart.LAZY) {
                suspend fun lookup(block: suspend ()->SharedPreview) {
                    repeat(3) { attempt ->
                        try {
                            val preview=slots.withPermit { block() }
                            ensureActive(); apply(video,preview); return
                        } catch(e: CancellationException) { throw e }
                        catch(e: Exception) {
                            if(attempt == 2) failed(e) else delay(retryDelay*(attempt+1))
                        }
                    }
                }
                coroutineScope {
                    if(!video.resolved && video.title == video.url) launch(start=CoroutineStart.UNDISPATCHED) { lookup { fetch(video) } }
                    if(fetchDuration != null && positiveDuration(video.duration) == null) launch(start=CoroutineStart.UNDISPATCHED) {
                        lookup { SharedPreview("","",positiveDuration(fetchDuration.invoke(video)) ?: error("再生時間を取得できませんでした。")) }
                    }
                }
            }
            jobs[video.key]=job
            job.start()
        }
    }
}

internal object QueuePreviews {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var started=false
    private val loader=SharedPreviewLoader(scope,::fetchSharedPreview,{ video,preview ->
        Store.state.update { it.withSharedPreview(video,preview) }; Store.save()
    },{ AppLog.error("動画プレビュー",it) },fetchDuration=::fetchYoutubeDuration)
    fun start() {
        if(started) return
        started=true
        scope.launch { Store.state.collect { loader.update(it.videos) } }
    }
}

internal suspend fun fetchYoutubeDuration(video: Video): Double? = withContext(Dispatchers.IO) {
    val id=requireNotNull(youtubeVideoId(video.url))
    val connection=URL("https://www.youtube.com/watch?v=$id&hl=en").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=10000; connection.readTimeout=10000
        connection.setRequestProperty("User-Agent","Mozilla/5.0")
        check(connection.responseCode == 200) { "再生時間の取得に失敗しました (${connection.responseCode})" }
        val text=connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val limit=3*1024*1024
            val buffer=CharArray(16384)
            val text=StringBuilder()
            var checkedHead=false
            while(text.length < limit) {
                val n=reader.read(buffer,0,minOf(buffer.size,limit-text.length))
                if(n < 0) break
                text.append(buffer,0,n)
                if(!checkedHead && text.indexOf("</head>") >= 0) {
                    checkedHead=true
                    youtubeDurationFromHtml(text.toString(),id)?.let { return@withContext it }
                }
            }
            text.toString()
        }
        youtubeDurationFromHtml(text,id)
    } finally { connection.disconnect() }
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
