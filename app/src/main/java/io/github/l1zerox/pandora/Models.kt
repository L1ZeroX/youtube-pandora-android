package io.github.l1zerox.pandora

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class Video(val key: String, val url: String, val title: String, val site: String = "",
    val duration: Double? = null, val thumbnail: String = "", val selected: Boolean = true,
    val status: String = "待機中", val error: String = "", val savedUri: String = "", val resolved: Boolean = true)
data class Preferences(val format: String = "MP4", val quality: String = "1080p以下",
    val audio: String = "192kbps", val fps: String = "元のまま", val dark: Boolean = true,
    val playlists: Boolean = false, val wallpaper: String = "", val opacity: Float = .35f,
    val glass: Float = .35f, val blur: Float = 18f, val bubble: Boolean = true,
    val bubbleClipboard: Boolean = true, val artworkMode: String = "サムネイル", val artworkImage: String = "",
    val libraryGlass: Float = .45f, val libraryBlur: Int = 10)
data class ScreenState(val videos: List<Video> = emptyList(), val busy: Boolean = false,
    val message: String = "URLを貼り付けて情報を取得してください。", val progress: Float? = null,
    val detail: String = "", val current: String = "", val done: Int = 0, val total: Int = 0,
    val preferences: Preferences = Preferences(), val incomingUrl: String = "",
    val paused: Boolean = false, val stopping: Boolean = false, val operation: String = "",
    val speed: String = "", val eta: Double? = null)

fun durationText(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0) return "時間不明"
    val seconds = value.toLong()
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
fun validUrls(text: String): List<String> = text.lines().map(String::trim).filter(String::isNotEmpty).distinct().onEach {
    val uri = runCatching { URI(it) }.getOrNull()
    require(uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "http:// または https:// から始まるURLを1行に1つ入力してください。"
    }
}
fun formatSelector(p: Preferences): String {
    if (p.format == "MP3") return "bestaudio/best"
    val height = when(p.quality) { "1080p以下" -> "[height<=1080]"; "720p以下" -> "[height<=720]"; else -> "" }
    return "bestvideo$height+bestaudio/best$height"
}
fun saveRelativePath(format: String): String = "Download/YoutubePandora!/${if(format == "MP4") "mp4" else "mp3"}/"
fun ScreenState.withoutSelectedVideos(): ScreenState =
    if(busy || videos.none { it.selected }) this else copy(videos=videos.filterNot { it.selected },
        message="${videos.count { it.selected }}件を一覧から削除しました。")
internal fun ScreenState.withDownloadFormat(format: String): ScreenState =
    if(busy || format !in listOf("MP4","MP3") || preferences.format == format) this
    else copy(preferences=preferences.copy(format=format))
internal fun ScreenState.withoutQueueVideo(key: String): ScreenState =
    if(busy || videos.none { it.key == key }) this
    else copy(videos=videos.filterNot { it.key == key },message="キューから削除しました。保存済みファイルは残っています。")
fun safeName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(150).ifBlank { "video" }

object Store {
    val state = MutableStateFlow(ScreenState())
    lateinit var context: Context
    fun init(context: Context) {
        this.context = context.applicationContext
        val data = context.getSharedPreferences("pandora", Context.MODE_PRIVATE)
        val p = runCatching { JSONObject(data.getString("preferences", "{}")!!) }.getOrDefault(JSONObject())
        val preferences = Preferences(p.optString("format", "MP4"), p.optString("quality", "1080p以下"),
            p.optString("audio", "192kbps"), p.optString("fps", "元のまま"), p.optBoolean("dark", true),
            p.optBoolean("playlists"), p.optString("wallpaper"), p.optDouble("opacity", .35).toFloat().coerceIn(0f,1f),
            p.optDouble("glass", .35).toFloat().coerceIn(0f,.85f), p.optDouble("blur", 18.0).toFloat().coerceIn(0f,40f),p.optBoolean("bubble",true),p.optBoolean("bubbleClipboard",true),
            p.optString("artworkMode","サムネイル").takeIf { it in listOf("サムネイル","ギャラリー","なし") } ?: "サムネイル",p.optString("artworkImage"),
            libraryPanelTransparency(p.optDouble("libraryGlass",.45).toFloat()),p.optInt("libraryBlur",10).coerceIn(0,20))
        val videos = runCatching {
            val array = JSONArray(data.getString("queue", "[]"))
            (0 until array.length()).map { index ->
                val v = array.getJSONObject(index)
                val interrupted=interruptedStatus(v.optString("status"))
                Video(v.getString("key"), v.getString("url"), v.getString("title"), v.optString("site"),
                    v.optDouble("duration").takeIf { it.isFinite() && it >= 0 }, v.optString("thumbnail"),
                    v.optBoolean("selected", true), if(interrupted) "失敗" else v.optString("status").takeIf { it in listOf("完了","失敗","一時停止","キャンセル") } ?: "待機中",
                    error=if(interrupted) "前回の処理中にアプリが終了しました。保存完了は確認できません。ログを確認し、再試行してください。" else v.optString("error"),savedUri=v.optString("savedUri"),resolved=v.optBoolean("resolved",true))
            }
        }.getOrDefault(emptyList())
        state.value = ScreenState(videos=videos, preferences=preferences).withoutSavedDownloads()
        val interrupted=videos.count { it.error.startsWith("前回の処理中にアプリが終了しました。") }
        if(interrupted > 0) {
            state.value=state.value.copy(message="前回の処理が中断されています。ログを確認し、再試行してください。")
        }
        // Persist recovered statuses and remove previously saved downloads from the queue.
        val raw=runCatching { JSONArray(data.getString("queue","[]")) }.getOrNull()
        val recovered=raw != null && (0 until raw.length()).any { interruptedStatus(raw.optJSONObject(it)?.optString("status").orEmpty()) }
        if(recovered) {
            AppLog.errorMessage("前回の処理中断","処理中の動画が残ったままアプリが再起動しました。保存完了ではありません。クラッシュ詳細・Androidの終了記録を確認してください。")
        }
        if(recovered || state.value.videos.size != videos.size) save()
    }
    fun edit(key: String, transform: (Video) -> Video) { state.update { s -> s.copy(videos=s.videos.map { if(it.key == key) transform(it) else it }) } }
    fun preferences(p: Preferences) { state.update { it.copy(preferences=p) }; save() }
    fun removeSelected() { state.update { it.withoutSelectedVideos() }; save() }
    fun downloadFormat(format: String) { state.update { it.withDownloadFormat(format) }; save() }
    fun removeQueueVideo(key: String) { state.update { it.withoutQueueVideo(key) }; save() }
    fun receiveShare(text: String): Int {
        var added=0
        state.update { s ->
            val videos=appendSharedVideos(s.videos,text)
            added=videos.size-s.videos.size
            s.copy(videos=videos,total=if(s.busy && s.operation == "DOWNLOAD") s.total+added else s.total,
                message=if(s.busy) s.message else if(added > 0) "$added 件をキューに追加しました。" else "追加できる新しいURLはありません。")
        }
        save(); return added
    }
    @Synchronized fun save() {
        val s = state.value; val p = s.preferences
        val config = JSONObject().put("format",p.format).put("quality",p.quality).put("audio",p.audio).put("fps",p.fps)
            .put("dark",p.dark).put("playlists",p.playlists).put("wallpaper",p.wallpaper)
            .put("opacity",p.opacity).put("glass",p.glass).put("blur",p.blur).put("bubble",p.bubble).put("bubbleClipboard",p.bubbleClipboard)
            .put("artworkMode",p.artworkMode).put("artworkImage",p.artworkImage)
            .put("libraryGlass",p.libraryGlass).put("libraryBlur",p.libraryBlur)
        val videos = JSONArray()
        s.videos.forEach { v -> videos.put(JSONObject().put("key",v.key).put("url",v.url).put("title",v.title)
            .put("site",v.site).put("duration",v.duration).put("thumbnail",v.thumbnail).put("selected",v.selected)
            .put("status",v.status).put("error",v.error).put("savedUri",v.savedUri).put("resolved",v.resolved)) }
        context.getSharedPreferences("pandora", Context.MODE_PRIVATE).edit().putString("preferences",config.toString())
            .putString("queue",videos.toString()).apply()
    }
}
class PandoraApp : Application() { override fun onCreate() { super.onCreate(); CrashDiagnostics.install(this); AppLog.init(this); Store.init(this); QueuePreviews.start() } }
