package io.github.l1zerox.pandora

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONObject

/** Called on IO. Cache only successful probes, invalidated by provider size/modification time. */
internal object LibraryMetadata {
    private const val CACHE_SCHEMA=3
    fun enrich(context: Context, entry: LibraryEntry, revision: String? = null): LibraryEntry {
        val uri=Uri.parse(entry.uri)
        val version=revision ?: runCatching {
            context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {
                if(it.moveToFirst() && !it.isNull(0) && !it.isNull(1)) "${it.getLong(0)}:${it.getLong(1)}" else null
            }
        }.getOrNull()
        val cache=context.getSharedPreferences("library_metadata",Context.MODE_PRIVATE)
        val confirmed=entry.video && SavedAudioMetadata.matches(context,entry.uri,version)
        val saved=runCatching { JSONObject(cache.getString(entry.uri,null) ?: "") }.getOrNull()
        if(version != null && saved?.optString("revision") == version && saved.optInt("schema") == CACHE_SCHEMA && saved.has("autoDubbed")) return entry.copy(
            width=saved.optInt("width"),height=saved.optInt("height"),
            autoDubbed=entry.video && (confirmed || saved.optBoolean("autoDubbed")),
            durationMs=entry.durationMs.takeIf { it > 0 } ?: saved.optLong("duration"))
        val measured=runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context,uri)
                val size=libraryDisplaySize(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0)
                val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                val autoDubbed=entry.video && isAutoDubbedMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
                entry.copy(width=size.first,height=size.second,durationMs=entry.durationMs.takeIf { it > 0 } ?: duration,autoDubbed=autoDubbed)
            }
        }.getOrDefault(entry)
        // Vendor metadata readers may omit the custom genre. The file's exact marker is still authoritative.
        val result=if(entry.video && !measured.autoDubbed && (confirmed || readDubbingMarker(context,uri))) measured.copy(autoDubbed=true) else measured
        if(version != null && (if(result.video) result.width > 0 else result.durationMs > 0)) {
            cache.edit().putString(entry.uri,JSONObject().put("schema",CACHE_SCHEMA).put("revision",version).put("width",result.width)
                .put("height",result.height).put("duration",result.durationMs).put("autoDubbed",result.autoDubbed).toString()).apply()
        }
        return result
    }

    private fun readDubbingMarker(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { asset ->
            asset.createInputStream().use { input -> mp4DubbingMarker(input.channel,asset.startOffset,asset.declaredLength) }
        } ?: false
    }.getOrDefault(false)

    fun remove(context: Context, uri: String) {
        context.getSharedPreferences("library_metadata",Context.MODE_PRIVATE).edit().remove(uri).apply()
        SavedAudioMetadata.remove(context,uri)
    }
}
