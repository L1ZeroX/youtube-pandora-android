package io.github.l1zerox.pandora

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

internal data class SavedDownload(val source: String, val format: String, val uri: String, val range: String = "")
internal fun savedSource(video: Video): String = youtubeVideoId(video.url)?.let { "youtube:$it" } ?: mediaIdentity(video.url)

/** Older app exports used either [youtube_ID] or [ID], with MediaStore's optional duplicate suffix. */
internal fun legacySavedSource(name: String): String? {
    val match=Regex("\\[(youtube_)?([A-Za-z0-9_-]{11})\\](?: \\(\\d+\\))?\\.(?:mp4|mp3)$",RegexOption.IGNORE_CASE).find(name) ?: return null
    val id=match.groupValues[2]
    // Do not mistake legacy numeric/Vimeo or Niconico identifiers for a YouTube ID.
    if(match.groupValues[1].isEmpty() && Regex("(?:(?:sm|nm|so)?[0-9]+)",RegexOption.IGNORE_CASE).matches(id)) return null
    return "youtube:$id"
}

internal fun List<SavedDownload>.containsDownload(video: Video, format: String, range: String = "")=
    any { it.source == savedSource(video) && it.format == format && it.range == range }

internal object SavedDownloads {
    val state=MutableStateFlow<List<SavedDownload>>(emptyList())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val changes=Channel<Unit>(Channel.CONFLATED)
    private val lock=Any()
    private lateinit var context: Application
    private var known=emptyList<SavedDownload>()
    private var started=false
    private fun prefs()=context.getSharedPreferences("saved_download_sources",0)
    fun init(context: Context) {
        if(started) return
        started=true; this.context=context.applicationContext as Application
        known=runCatching {
            val data=JSONArray(prefs().getString("items","[]"))
            (0 until data.length()).map { data.getJSONObject(it).let { item ->
                SavedDownload(item.getString("source"),item.getString("format"),item.getString("uri"),item.optString("range"))
            } }
        }.getOrDefault(emptyList())
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { changes.trySend(Unit) }
        }
        this.context.contentResolver.registerContentObserver(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),true,observer)
        scope.launch { for(ignored in changes) { delay(200); refresh() } }
        changes.trySend(Unit)
    }
    private suspend fun refresh() {
        try {
            val collection=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val found=mutableListOf<Pair<String,Pair<String,String>>>()
            val columns=arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE)
            context.contentResolver.query(collection,columns,
                "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?,?) AND ${MediaStore.MediaColumns.IS_PENDING}=0 AND ${MediaStore.MediaColumns.SIZE}>0",
                arrayOf(saveRelativePath("MP4"),saveRelativePath("MP3")),null)?.use { cursor ->
                while(cursor.moveToNext()) {
                    val name=cursor.getString(1).orEmpty()
                    val format=when(libraryMime(cursor.getString(2),name)) { "video/mp4" -> "MP4"; "audio/mpeg" -> "MP3"; else -> continue }
                    found+=ContentUris.withAppendedId(collection,cursor.getLong(0)).toString() to (name to format)
                }
            } ?: return
            synchronized(lock) {
                val recorded=known.associateBy { it.uri }
                state.value=found.mapNotNull { (uri,info) ->
                    recorded[uri] ?: legacySavedSource(info.first)?.let { SavedDownload(it,info.second,uri) }
                }
            }
        } catch(e: Exception) { AppLog.error("保存済み確認",e) }
    }
    fun record(video: Video, format: String, uri: String, range: String = "") {
        synchronized(lock) {
            val entry=SavedDownload(savedSource(video),format,uri,range)
            known=(known.filterNot { it.uri == uri }+entry).takeLast(20000)
            val array=JSONArray()
            known.forEach { array.put(JSONObject().put("source",it.source).put("format",it.format).put("uri",it.uri).put("range",it.range)) }
            prefs().edit().putString("items",array.toString()).apply()
            state.value=state.value.filterNot { it.uri == uri }+entry
        }
        changes.trySend(Unit)
    }
    fun excludeSaved() {
        Store.state.update { screen ->
            if(screen.busy) screen else screen.copy(videos=screen.videos.map { video ->
                if(state.value.containsDownload(video,screen.preferences.format,screen.preferences.saveRange.identity())) video.copy(selected=false) else video
            })
        }
        Store.save()
    }
}

@Composable
internal fun SavedDownloadBadge(video: Video) {
    val saved by SavedDownloads.state.collectAsStateWithLifecycle()
    val screen by Store.state.collectAsStateWithLifecycle()
    val format=screen.preferences.format
    if(saved.containsDownload(video,format,screen.preferences.saveRange.identity()))
        Text("保存済み（$format${if(screen.preferences.saveRange.enabled) "・同じ範囲" else ""}）· 再DLする場合のみ選択",fontSize=12.sp)
}
