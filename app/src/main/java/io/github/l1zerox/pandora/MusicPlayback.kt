package io.github.l1zerox.pandora

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay

internal data class MusicQueueItem(val uri: String, val title: String)
internal data class MusicPlaybackState(val uri: String = "", val artist: String = "", val playing: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val seekable: Boolean = false, val canPlay: Boolean = false,
    val canPrevious: Boolean = false, val canNext: Boolean = false, val canShuffle: Boolean = false,
    val canRepeat: Boolean = false, val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MusicQueueItem> = emptyList(), val canSelect: Boolean = false)

/** Operates the Activity's existing MediaController; never creates another player or session. */
internal class MusicPlayback(private val player: Player?) {
    var state by mutableStateOf(MusicPlaybackState())
        private set

    fun refresh(queueChanged: Boolean = false) {
        val p=player ?: return
        val duration=p.duration.coerceAtLeast(0)
        val queue=if(queueChanged) (0 until p.mediaItemCount).map { index ->
            val item=p.getMediaItemAt(index)
            MusicQueueItem(item.mediaId,item.mediaMetadata.title?.toString().orEmpty().ifBlank { "音楽" })
        } else state.queue
        state=MusicPlaybackState(p.currentMediaItem?.mediaId.orEmpty(),p.mediaMetadata.artist?.toString().orEmpty(),
            p.playWhenReady && p.playbackState !in listOf(Player.STATE_IDLE,Player.STATE_ENDED),
            p.currentPosition.coerceIn(0,duration),duration,
            duration > 0 && p.isCurrentMediaItemSeekable && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            p.mediaItemCount > 0 && p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            p.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),p.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
            p.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE),p.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE),
            p.shuffleModeEnabled,p.repeatMode,queue,p.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
    }
    fun toggle() {
        val p=player ?: return
        if(p.currentMediaItem == null || !p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return
        if(p.playWhenReady && p.playbackState !in listOf(Player.STATE_IDLE,Player.STATE_ENDED)) p.pause()
        else {
            if(p.playbackState == Player.STATE_ENDED && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) p.seekTo(0)
            if(p.playbackState == Player.STATE_IDLE && p.isCommandAvailable(Player.COMMAND_PREPARE)) p.prepare()
            p.play()
        }
        refresh()
    }
    fun seek(expectedUri: String, fraction: Float) {
        val p=player ?: return
        val duration=p.duration
        if(p.currentMediaItem?.mediaId != expectedUri || !fraction.isFinite() || duration <= 0 ||
            !p.isCurrentMediaItemSeekable || !p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        p.seekTo((duration*fraction.coerceIn(0f,1f)).toLong()); refresh()
    }
    fun previous() { player?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) }?.seekToPrevious(); refresh() }
    fun next() { player?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) }?.seekToNext(); refresh() }
    fun shuffle() {
        player?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE) }?.let { it.shuffleModeEnabled=!it.shuffleModeEnabled }
        refresh()
    }
    fun repeat() {
        player?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) }?.let {
            it.repeatMode=when(it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
        }
        refresh()
    }
    fun select(uri: String) {
        val p=player ?: return
        if(!p.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) || !p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return
        val index=(0 until p.mediaItemCount).firstOrNull { p.getMediaItemAt(it).mediaId == uri } ?: return
        p.seekToDefaultPosition(index)
        if(p.playbackState == Player.STATE_IDLE && p.isCommandAvailable(Player.COMMAND_PREPARE)) p.prepare()
        p.play(); refresh()
    }
}

@Composable
internal fun rememberMusicPlayback(player: Player?): MusicPlayback {
    val transport=remember(player) { MusicPlayback(player) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player,transport,lifecycle) {
        val listener=object: Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { transport.refresh(queueChanged=true) }
        }
        var attached=false
        fun attach() { if(!attached) { player?.addListener(listener); attached=true; transport.refresh(queueChanged=true) } }
        fun detach() { if(attached) { player?.removeListener(listener); attached=false } }
        val observer=LifecycleEventObserver { _,event ->
            if(event == Lifecycle.Event.ON_START) attach()
            if(event == Lifecycle.Event.ON_STOP) detach()
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) attach()
        onDispose { detach(); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(transport,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { transport.refresh(); delay(250) }
        }
    }
    return transport
}
