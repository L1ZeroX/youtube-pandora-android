package io.github.l1zerox.pandora

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

/** Debug-only fixture for exercising the real legacy browse/transport bridge with generated media. */
@androidx.annotation.OptIn(UnstableApi::class)
class CarBrowseFixtureService: MediaLibraryService() {
    private var session: MediaLibrarySession?=null
    private var callback: CarLibraryCallback?=null
    override fun onCreate() {
        super.onCreate()
        val snapshot=entries.toList()
        val player=ExoPlayer.Builder(this).build().apply { volume=0f }
        callback=CarLibraryCallback(this,{snapshot},{LibraryCollections()})
        session=MediaLibrarySession.Builder(this,player,callback!!).setId("car-browse-fixture").build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo)=session
    override fun onDestroy() {
        callback?.close(); callback=null
        session?.run { player.release(); release() }; session=null
        super.onDestroy()
    }
    companion object { @Volatile internal var entries: List<LibraryEntry> = emptyList() }
}
