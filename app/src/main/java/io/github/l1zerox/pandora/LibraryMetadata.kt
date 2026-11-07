package io.github.l1zerox.pandora

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONObject

/** Called on IO. Cache only successful probes, invalidated by provider size/modification time. */
internal object LibraryMetadata {
    fun enrich(context: Context, entry: LibraryEntry, revision: String? = null): LibraryEntry {
        val uri=Uri.parse(entry.uri)
        val version=revision ?: runCatching {
            context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {
                if(it.moveToFirst() && !it.isNull(0) && !it.isNull(1)) "${it.getLong(0)}:${it.getLong(1)}" else null
            }
        }.getOrNull()
        val cache=context.getSharedPreferences("library_metadata",Context.MODE_PRIVATE)
        val saved=runCatching { JSONObject(cache.getString(entry.uri,null) ?: "") }.getOrNull()
        if(version != null && saved?.optString("revision") == version) return entry.copy(
            width=saved.optInt("width"),height=saved.optInt("height"),
            durationMs=entry.durationMs.takeIf { it > 0 } ?: saved.optLong("duration"))
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context,uri)
                val size=libraryDisplaySize(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0)
                val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                if(version != null && (if(entry.video) size.first > 0 else duration > 0)) {
                    cache.edit().putString(entry.uri,JSONObject().put("revision",version).put("width",size.first)
                        .put("height",size.second).put("duration",duration).toString()).apply()
                }
                entry.copy(width=size.first,height=size.second,durationMs=entry.durationMs.takeIf { it > 0 } ?: duration)
            }
        }.getOrDefault(entry)
    }

    fun remove(context: Context, uri: String) {
        context.getSharedPreferences("library_metadata",Context.MODE_PRIVATE).edit().remove(uri).apply()
    }
}
