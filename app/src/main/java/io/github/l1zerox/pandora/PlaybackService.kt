package io.github.l1zerox.pandora

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow

internal data class PlaybackStatus(val uri: String = "", val title: String = "", val playing: Boolean = false)
internal object PlaybackRuntime {
    val state=MutableStateFlow(PlaybackStatus())
    // Main-thread callback owned by the service; deletion also works while the UI controller is disconnected.
    var removeLibraryItem: ((String)->Unit)? = null
}

internal fun removeLibraryMedia(player: Player, uri: String) {
    if(player.currentMediaItem?.mediaId == uri) player.pause()
    for(index in player.mediaItemCount-1 downTo 0) {
        if(player.getMediaItemAt(index).mediaId == uri) player.removeMediaItem(index)
    }
    if(player.mediaItemCount == 0) player.stop()
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val player=ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build()
        player.addListener(object: Player.Listener {
            override fun onPlayerError(error: PlaybackException) { AppLog.error("ライブラリー再生",error) }
            override fun onEvents(player: Player, events: Player.Events) {
                PlaybackRuntime.state.value=PlaybackStatus(player.currentMediaItem?.mediaId.orEmpty(),
                    player.mediaMetadata.title?.toString().orEmpty(),player.isPlaying)
            }
        })
        val open=PendingIntent.getActivity(this,40,Intent(this,PlayerActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session=MediaSession.Builder(this,player).setSessionActivity(open).build()
        PlaybackRuntime.removeLibraryItem={ uri -> removeLibraryMedia(player,uri) }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        PlaybackRuntime.removeLibraryItem=null
        session?.run { player.release(); release() }; session=null
        PlaybackRuntime.state.value=PlaybackStatus()
        super.onDestroy()
    }
}
