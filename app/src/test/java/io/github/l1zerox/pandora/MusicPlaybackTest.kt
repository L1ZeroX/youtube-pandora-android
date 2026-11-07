package io.github.l1zerox.pandora

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class MusicPlaybackTest {
    private class Session {
        val items=listOf("a","b").map { MediaItem.Builder().setMediaId(it).setMediaMetadata(
            MediaMetadata.Builder().setTitle("Track $it").setArtist("Artist $it").build()).build() }
        var index=0
        var position=4000L
        var duration=10000L
        var ready=true
        var playbackState=Player.STATE_READY
        var shuffle=false
        var repeat=Player.REPEAT_MODE_OFF
        var seekable=true
        val denied=mutableSetOf<Int>()
        val calls=mutableListOf<String>()
        val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentMediaItem" -> items[index]
                "getMediaMetadata" -> items[index].mediaMetadata
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args!![0] as Int]
                "getDuration" -> duration
                "getCurrentPosition" -> position
                "getPlayWhenReady" -> ready
                "getPlaybackState" -> playbackState
                "isCurrentMediaItemSeekable" -> seekable
                "isCommandAvailable" -> args!![0] !in denied
                "getShuffleModeEnabled" -> shuffle
                "getRepeatMode" -> repeat
                "setShuffleModeEnabled" -> { shuffle=args!![0] as Boolean; calls+="shuffle"; null }
                "setRepeatMode" -> { repeat=args!![0] as Int; calls+="repeat"; null }
                "play" -> { ready=true; calls+="play"; null }
                "pause" -> { ready=false; calls+="pause"; null }
                "prepare" -> { playbackState=Player.STATE_READY; calls+="prepare"; null }
                "seekTo" -> { position=args!![0] as Long; calls+="seek"; null }
                "seekToPrevious" -> { index=0; calls+="previous"; null }
                "seekToNext" -> { index=1; calls+="next"; null }
                "seekToDefaultPosition" -> { index=args!![0] as Int; position=0; calls+="select"; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        val transport=MusicPlayback(player).apply { refresh(queueChanged=true) }
    }
    @Test fun stateTracksPlaybackPositionArtistAndQueueWithoutCreatingAPlayer() {
        val s=Session()
        assertEquals("Artist a",s.transport.state.artist)
        assertEquals(listOf("a","b"),s.transport.state.queue.map { it.uri })
        assertTrue(s.transport.state.playing)
        s.position=6500; s.transport.refresh()
        assertEquals(6500,s.transport.state.position)
        s.transport.toggle(); assertFalse(s.transport.state.playing)
        s.transport.toggle(); assertTrue(s.transport.state.playing)
        assertEquals(listOf("pause","play"),s.calls)
    }
    @Test fun seekClampsPositionAndRejectsStaleOrInvalidDrags() {
        val s=Session()
        s.transport.seek("a",.5f); assertEquals(5000,s.position)
        s.transport.next()
        s.transport.seek("a",0f); s.transport.seek("b",Float.NaN)
        assertEquals(5000,s.position)
        s.transport.seek("b",2f); assertEquals(10000,s.position)
        s.duration=C.TIME_UNSET; s.transport.refresh()
        assertFalse(s.transport.state.seekable); assertEquals(0,s.transport.state.duration)
        s.transport.seek("b",0f); assertEquals(10000,s.position)
    }
    @Test fun shuffleRepeatNavigationAndQueueSelectionOperateOnTheSameSession() {
        val s=Session()
        s.transport.shuffle(); assertTrue(s.shuffle)
        s.transport.repeat(); assertEquals(Player.REPEAT_MODE_ALL,s.repeat)
        s.transport.repeat(); assertEquals(Player.REPEAT_MODE_ONE,s.repeat)
        s.transport.repeat(); assertEquals(Player.REPEAT_MODE_OFF,s.repeat)
        s.transport.next(); assertEquals("b",s.transport.state.uri)
        s.transport.previous(); assertEquals("a",s.transport.state.uri)
        s.ready=false; s.transport.select("b"); assertEquals(1,s.index); assertTrue(s.ready)
        s.transport.select("deleted"); assertEquals(1,s.index)
    }
    @Test fun deniedCommandsAreNotSentAndEndedOrIdlePlaybackCanRestart() {
        val s=Session()
        s.denied+=listOf(Player.COMMAND_PLAY_PAUSE,Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SET_SHUFFLE_MODE,
            Player.COMMAND_SET_REPEAT_MODE,Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        s.transport.toggle(); s.transport.seek("a",.5f); s.transport.previous(); s.transport.next()
        s.transport.shuffle(); s.transport.repeat(); s.transport.select("b")
        assertTrue(s.calls.isEmpty())
        s.denied.clear(); s.playbackState=Player.STATE_ENDED
        s.transport.toggle(); assertEquals(listOf("seek","play"),s.calls); assertEquals(0,s.position)
        s.calls.clear(); s.playbackState=Player.STATE_IDLE
        s.transport.toggle(); assertEquals(listOf("prepare","play"),s.calls)
    }
}
