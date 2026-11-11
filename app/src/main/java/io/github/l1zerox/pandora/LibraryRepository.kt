package io.github.l1zerox.pandora

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal object LibraryRepository {
    private val importLock=Any()
    private fun preferences(context: Context) = context.getSharedPreferences("library",Context.MODE_PRIVATE)

    suspend fun load(context: Context): List<LibraryEntry> = withContext(Dispatchers.IO) {
        val collection=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val columns=arrayOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.DATE_ADDED,MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_MODIFIED)
        val downloads=mutableListOf<LibraryEntry>()
        val queue=Store.state.value.videos.associateBy { it.savedUri }
        context.contentResolver.query(collection,columns,
            "${MediaStore.MediaColumns.RELATIVE_PATH} IN (?,?) AND ${MediaStore.MediaColumns.IS_PENDING}=0",
            arrayOf(saveRelativePath("MP4"),saveRelativePath("MP3")),null)?.use { cursor ->
            while(cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val uri=ContentUris.withAppendedId(collection,cursor.getLong(0)).toString()
                val name=cursor.getString(1).orEmpty()
                val mime=libraryMime(cursor.getString(2),name) ?: continue
                val saved=queue[uri]
                val entry=LibraryEntry(uri,saved?.title ?: name.substringBeforeLast('.'),mime,
                    cursor.getLong(4).takeIf { it > 0 } ?: ((saved?.duration ?: 0.0)*1000).toLong(),cursor.getLong(3))
                downloads+=LibraryMetadata.enrich(context,entry,"${cursor.getLong(5)}:${cursor.getLong(6)}")
            }
        }
        libraryEntries(downloads,readImports(context).map {
            currentCoroutineContext().ensureActive()
            LibraryMetadata.enrich(context,it)
        })
    }

    private fun readImports(context: Context): List<LibraryEntry> = runCatching {
        val array=JSONArray(preferences(context).getString("items","[]"))
        (0 until array.length()).mapNotNull { index -> runCatching {
            val item=array.getJSONObject(index)
            LibraryEntry(item.getString("uri"),item.getString("title"),item.getString("mime"),
                item.optLong("duration"),item.optLong("added"),true)
        }.getOrNull() }
    }.getOrDefault(emptyList())

    suspend fun addFiles(context: Context, uris: List<Uri>, section: LibrarySection? = null): Int = withContext(Dispatchers.IO) {
        val entries=mutableMapOf<String,LibraryEntry>()
        val existing=readImports(context).map { it.uri }.toSet()
        var failed=0
        for(uri in uris.distinct()) {
            currentCoroutineContext().ensureActive()
            try {
                require(uri.scheme == "content")
                val resolver=context.contentResolver
                if(uri.toString() in existing) {
                    resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    continue
                }
                val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                    if(it.moveToFirst()) it.getString(0) else null
                } ?: "メディア"
                val mime=libraryMime(resolver.getType(uri),name) ?: error("非対応のファイル")
                require(section == null || mime.startsWith(if(section == LibrarySection.VIDEO) "video/" else "audio/"))
                resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Providers may grant only read access; importing must still work in that case.
                runCatching { resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                entries[uri.toString()]=LibraryEntry(uri.toString(),name.substringBeforeLast('.'),mime,
                    added=System.currentTimeMillis()/1000,imported=true)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { failed++; AppLog.info("ライブラリー","ファイルを追加できません: ${e.javaClass.simpleName}") }
        }
        synchronized(importLock) {
            val merged=readImports(context).associateBy { it.uri }.toMutableMap()
            merged.putAll(entries)
            writeImports(context,merged.values.toList())
        }
        failed
    }

    private fun writeImports(context: Context, entries: List<LibraryEntry>) {
        val array=JSONArray()
        entries.forEach { item -> array.put(JSONObject().put("uri",item.uri).put("title",item.title)
            .put("mime",item.mime).put("duration",item.durationMs).put("added",item.added)) }
        preferences(context).edit().putString("items",array.toString()).apply()
    }

    internal fun forgetImport(context: Context, uri: String) {
        synchronized(importLock) { writeImports(context,readImports(context).filterNot { it.uri == uri }) }
        LibraryMetadata.remove(context,uri)
    }

    suspend fun artwork(context: Context, entry: LibraryEntry): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context,Uri.parse(entry.uri))
                if(entry.video) retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,480,270)
                else retriever.embeddedPicture?.let { bytes ->
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    val options=BitmapFactory.Options().apply {
                        inSampleSize=1
                        while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize > 768) inSampleSize*=2
                    }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                }
            }
        }.getOrNull()
    }
}
