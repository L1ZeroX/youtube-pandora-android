package io.github.l1zerox.pandora

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import java.io.File

internal const val PLAYBACK_ARTWORK_REVISION="pandora.playbackArtworkRevision"

/** Publish custom artwork to the playing item's session metadata, also used by car dashboard cards. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackArtworkSync(private val context: Context, private val player: Player) : Player.Listener {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val requests=ArtworkRequest()
    private var job: Job?=null
    private var publishing=false
    private data class Input(val item: MediaItem, val covers: Map<String,String>, val albums: List<UserPlaylist>)
    private data class Prepared(val uri: Uri, val bytes: ByteArray?, val revision: String?)
    init {
        player.addListener(this)
        scope.launch {
            combine(LibraryArtwork.state,LibraryExtras.state.map { it.albums }.distinctUntilChanged()) { _,_ -> Unit }
                .collect { refresh() }
        }
        refresh()
    }
    override fun onEvents(player: Player, events: Player.Events) {
        if(events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION,Player.EVENT_TIMELINE_CHANGED,Player.EVENT_MEDIA_METADATA_CHANGED)) refresh()
    }
    private fun refresh() {
        if(publishing) return
        // Keep inline JPEG data on the current item only, even after a long listening session.
        publishing=true
        try {
            for(index in 0 until player.mediaItemCount) if(index != player.currentMediaItemIndex) {
                val item=player.getMediaItemAt(index)
                if(item.mediaMetadata.artworkData != null && item.mediaMetadata.extras?.containsKey(PLAYBACK_ARTWORK_REVISION) == true)
                    player.replaceMediaItem(index,item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkData(null,null).build()).build())
            }
        } finally { publishing=false }
        val item=player.currentMediaItem
        if(item == null || item.mediaMetadata.extras?.getBoolean("video") == true || item.localConfiguration?.mimeType?.startsWith("video/") == true) {
            requests.invalidate(); job?.cancel(); return
        }
        val input=Input(item,LibraryArtwork.state.value,LibraryExtras.state.value.albums)
        val ticket=requests.begin(item.artworkEntry(),input) ?: return
        job?.cancel()
        job=scope.launch {
            try {
                val prepared=withContext(Dispatchers.IO) { prepare(item) } ?: return@launch
                if(!requests.accepts(ticket) || player.currentMediaItem != item || LibraryArtwork.state.value != input.covers || LibraryExtras.state.value.albums != input.albums) return@launch
                val extras=Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
                    remove(PLAYBACK_ARTWORK_REVISION)
                    prepared.revision?.let { putString(PLAYBACK_ARTWORK_REVISION,it) }
                }
                val metadata=item.mediaMetadata.buildUpon().setArtworkUri(prepared.uri)
                    .setArtworkData(prepared.bytes,if(prepared.bytes == null) null else MediaMetadata.PICTURE_TYPE_FRONT_COVER).setExtras(extras).build()
                val updated=item.buildUpon().setMediaMetadata(metadata).build()
                if(updated == item) return@launch
                // Acknowledge our own metadata before replacing, avoiding a decode/replacement loop.
                requests.begin(updated.artworkEntry(),input.copy(item=updated))
                publishing=true
                try { player.replaceMediaItem(player.currentMediaItemIndex,updated) }
                finally { publishing=false }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(requests.accepts(ticket)) AppLog.error("再生アートワーク",e) }
        }
    }
    private fun prepare(item: MediaItem): Prepared? {
        var entry=item.artworkEntry()
        if(entry.album.isBlank() && LibraryArtwork.state.value.isNotEmpty() && LibraryArtwork.file(entry) == null)
            entry=LibraryMetadata.enrich(context,entry)
        val image=LibraryArtwork.file(entry)
        if(image == null) {
            val previous=item.mediaMetadata.artworkUri
            val legacy=previous?.scheme == "file" && previous.path?.startsWith(File(context.filesDir,"artwork").path+"/") == true
            if(!legacy && item.mediaMetadata.extras?.containsKey(PLAYBACK_ARTWORK_REVISION) != true) return null
            // Clearing our override lets the player's embedded artwork take precedence again.
            return Prepared(carArtworkUri(context,entry,null,context.packageName),null,null)
        }
        val bytes=loadArtworkBitmap(context,Uri.fromFile(image),256).let { bitmap ->
            try { ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG,80,output))
                output.toByteArray().also { check(it.size in 1..65_536) }
            } } finally { bitmap.recycle() }
        }
        return Prepared(carArtworkUri(context,entry,null,context.packageName),bytes,image.name)
    }
    fun close() { requests.invalidate(); scope.cancel(); player.removeListener(this) }
}
