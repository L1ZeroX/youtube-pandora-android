package io.github.l1zerox.pandora

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

internal fun albumArtworkKey(id: String)="album:$id"
internal fun trackArtworkKey(uri: String)="track:$uri"
internal fun libraryArtworkKeys(entry: LibraryEntry, data: LibraryCollections): List<String> {
    if(entry.video) return emptyList()
    val manual=data.albums.firstOrNull { entry.uri in it.uris }
    return listOfNotNull(trackArtworkKey(entry.uri),manual?.let { albumArtworkKey(it.id) },
        if(manual == null) albumArtworkKey(if(albumNameKey(entry.album).isNotBlank()) albumId(albumNameKey(entry.album)) else "album-unknown") else null)
}

/** App-local display overrides; the imported media and its embedded tags are never rewritten. */
internal object LibraryArtwork {
    val state=MutableStateFlow<Map<String,String>>(emptyMap())
    private lateinit var prefs: SharedPreferences
    private lateinit var directory: File
    fun init(context: Context) {
        directory=File(context.filesDir,"artwork").canonicalFile
        prefs=context.getSharedPreferences("library_artwork",0)
        state.value=runCatching {
            val json=JSONObject(prefs.getString("covers","{}")!!)
            json.keys().asSequence().associateWith { json.getString(it) }.filterValues(::validName)
        }.getOrDefault(emptyMap())
    }
    private fun validName(name: String)=name.startsWith("cover-") && name.endsWith(".jpg") && '/' !in name && '\\' !in name && name != ".."
    fun file(key: String): File? {
        if(!::directory.isInitialized) return null
        return state.value[key]?.takeIf(::validName)?.let { File(directory,it) }?.takeIf { it.isFile }
    }
    fun file(entry: LibraryEntry)=libraryArtworkKeys(entry,LibraryExtras.state.value).firstNotNullOfOrNull(::file)
    @Synchronized fun set(key: String, image: File?) {
        check(::prefs.isInitialized)
        require(key.startsWith("album:") || key.startsWith("track:"))
        if(image != null) require(image.isFile && image.parentFile?.canonicalFile == directory && validName(image.name))
        val previous=state.value[key]
        val next=state.value.toMutableMap().apply { if(image == null) remove(key) else put(key,image.name) }
        val editor=prefs.edit()
        editor.putString("covers",JSONObject(next as Map<*,*>).toString())
        check(editor.commit()) { "アートワークを保存できませんでした。" }
        state.value=next
        if(previous != null && previous !in next.values && validName(previous)) File(directory,previous).delete()
    }
}

@Composable
internal fun rememberLibraryArtwork(entry: LibraryEntry?, album: String? = null): State<Bitmap?> {
    val context=LocalContext.current
    val covers by LibraryArtwork.state.collectAsStateWithLifecycle()
    val collections by LibraryExtras.state.collectAsStateWithLifecycle()
    return produceState<Bitmap?>(null,entry,album,covers,collections.albums) {
        value=null
        if(entry != null) value=LibraryRepository.artwork(context,entry,album)
    }
}
