package io.github.l1zerox.pandora

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** A confirmed selection belongs to one saved file, never to all downloads of a video. */
internal object SavedAudioMetadata {
    private val changed=MutableStateFlow(0L)
    val changes=changed.asStateFlow()
    private fun preferences(context: Context)=context.getSharedPreferences("saved_audio_metadata",Context.MODE_PRIVATE)

    // Called on IO, after publishing and before removing the queue item.
    fun recordAutoDubbed(context: Context, uri: String) {
        val version=revision(context,uri) ?: error("保存ファイルの更新情報を取得できませんでした。")
        val value=JSONObject().put("revision",version).put("autoDubbed",true).toString()
        check(preferences(context).edit().putString(uri,value).commit()) { "音声の判定結果を記録できませんでした。" }
        // MediaStore's publication notification may have arrived before this record.
        changed.update { it+1 }
    }

    fun matches(context: Context, uri: String, revision: String?): Boolean =
        savedAutoDubbedMatches(preferences(context).getString(uri,null),revision)

    fun revision(context: Context, uri: String): String? = runCatching {
        context.contentResolver.query(Uri.parse(uri),arrayOf(MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_MODIFIED),null,null,null)?.use {
            if(it.moveToFirst() && !it.isNull(0) && !it.isNull(1) && it.getLong(0) > 0) "${it.getLong(0)}:${it.getLong(1)}" else null
        }
    }.getOrNull()

    fun remove(context: Context, uri: String) { preferences(context).edit().remove(uri).apply() }
}

internal fun savedAutoDubbedMatches(json: String?, revision: String?): Boolean {
    if(revision.isNullOrBlank() || json == null) return false
    return runCatching {
        val saved=JSONObject(json)
        saved.optString("revision") == revision && saved.optBoolean("autoDubbed")
    }.getOrDefault(false)
}
