package io.github.l1zerox.pandora

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

/** A controller for the existing session; the library never creates another player. */
internal class LibraryPlayback(private val context: Context, videoOwnership: MutableState<Boolean> = mutableStateOf(false)) {
    var controller: MediaController? by mutableStateOf(null)
        private set
    var uri by mutableStateOf("")
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0)
        private set
    var duration by mutableLongStateOf(0)
        private set
    var seekable by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    private var future: ListenableFuture<MediaController>? = null
    private var resumedVideo by videoOwnership
    private val listener=object: Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }
    fun connect() {
        if(future != null) return
        val pending=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
        future=pending
        pending.addListener({
            if(future !== pending) return@addListener
            runCatching {
                controller=pending.get()
                controller?.addListener(listener)
                error=""; refresh()
            }.onFailure { error="再生操作に接続できませんでした。画面を開き直してください。"; AppLog.error("ライブラリー操作",it) }
        },ContextCompat.getMainExecutor(context))
    }
    fun refresh() {
        val player=controller ?: return
        uri=player.currentMediaItem?.mediaId.orEmpty()
        playing=player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE
        duration=player.duration.coerceAtLeast(0)
        position=player.currentPosition.coerceIn(0,duration.coerceAtLeast(0))
        seekable=duration > 0 && player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        error=if(player.playerError != null) "再生できません。ファイルの場所・アクセス権を確認してください。" else ""
    }
    fun toggle(expectedUri: String) {
        val player=controller ?: return
        if(player.currentMediaItem?.mediaId != expectedUri || !player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return
        if(playing) player.pause() else {
            if(player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if(player.playbackState == Player.STATE_IDLE) player.prepare()
            resumedVideo=player.currentMediaItem?.mediaMetadata?.extras?.getBoolean("video") == true
            player.play()
        }
        refresh()
    }
    fun seek(expectedUri: String, fraction: Float) {
        val player=controller ?: return
        // A drag begun on the previous track must never seek the next track.
        val currentDuration=player.duration
        if(player.currentMediaItem?.mediaId != expectedUri || !fraction.isFinite() || currentDuration <= 0 ||
            !player.isCurrentMediaItemSeekable || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        player.seekTo(playbackPositionMs(currentDuration,fraction)); refresh()
    }
    fun handOffToPlayer() { resumedVideo=false }
    fun disconnect() {
        val player=controller
        val rotating=(context as? Activity)?.isChangingConfigurations == true
        if(resumedVideo && !rotating &&
            player?.currentMediaItem?.mediaMetadata?.extras?.getBoolean("video") == true) player.pause()
        if(!rotating) resumedVideo=false
        player?.removeListener(listener)
        controller=null
        val pending=future; future=null
        pending?.let { MediaController.releaseFuture(it) }
    }
}

@Composable
internal fun rememberLibraryPlayback(): LibraryPlayback {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val videoOwnership=rememberSaveable { mutableStateOf(false) }
    val playback=remember(context) { LibraryPlayback(context,videoOwnership) }
    DisposableEffect(playback,lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_START -> playback.connect()
                Lifecycle.Event.ON_STOP -> playback.disconnect()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playback.connect()
        onDispose { lifecycle.removeObserver(observer); playback.disconnect() }
    }
    LaunchedEffect(playback.controller) {
        if(playback.controller != null) while(true) { playback.refresh(); delay(250) }
    }
    return playback
}
