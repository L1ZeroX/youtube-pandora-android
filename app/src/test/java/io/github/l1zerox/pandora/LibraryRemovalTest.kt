package io.github.l1zerox.pandora

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class LibraryRemovalTest {
    private class Session(ids: List<String>, private val current: String) {
        val items=ids.map { MediaItem.Builder().setMediaId(it).build() }.toMutableList()
        var paused=false
        var stopped=false
        val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentMediaItem" -> items.firstOrNull { it.mediaId == current }
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args!![0] as Int]
                "removeMediaItem" -> { items.removeAt(args!![0] as Int); null }
                "pause" -> { paused=true; null }
                "stop" -> { stopped=true; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }
    @Test fun deletingCurrentMediaPausesAndRemovesAllItsPlaylistOccurrences() {
        val session=Session(listOf("a","b","a","c"),"a")
        removeLibraryMedia(session.player,"a")
        assertEquals(listOf("b","c"),session.items.map { it.mediaId })
        assertTrue(session.paused); assertFalse(session.stopped)
    }
    @Test fun deletingAnotherItemDoesNotInterruptCurrentPlayback() {
        val session=Session(listOf("a","b","c"),"a")
        removeLibraryMedia(session.player,"b")
        assertEquals(listOf("a","c"),session.items.map { it.mediaId })
        assertFalse(session.paused); assertFalse(session.stopped)
    }
    @Test fun deletingTheLastItemStopsAnEmptySessionAndUnknownItemsAreHarmless() {
        val session=Session(listOf("a"),"a")
        removeLibraryMedia(session.player,"missing")
        assertEquals(1,session.items.size); assertFalse(session.paused)
        removeLibraryMedia(session.player,"a")
        assertTrue(session.items.isEmpty()); assertTrue(session.paused); assertTrue(session.stopped)
    }
}
