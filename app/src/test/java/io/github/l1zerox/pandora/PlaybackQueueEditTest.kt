package io.github.l1zerox.pandora

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueEditTest {
    private class Queue {
        val items=mutableListOf("a","b","c","d").map { MediaItem.Builder().setMediaId(it).build() }.toMutableList()
        val current="b"
        var shuffle=false
        val denied=mutableSetOf<Int>()
        val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentMediaItem" -> items.first { it.mediaId == current }
                "getCurrentMediaItemIndex" -> items.indexOfFirst { it.mediaId == current }
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args!![0] as Int]
                "isCommandAvailable" -> args!![0] !in denied
                "getShuffleModeEnabled" -> shuffle
                "setShuffleModeEnabled" -> { shuffle=args!![0] as Boolean; null }
                "moveMediaItem" -> { val item=items.removeAt(args!![0] as Int); items.add(args[1] as Int,item); null }
                "addMediaItem" -> { items.add(args!![0] as Int,args[1] as MediaItem); null }
                // A queue edit must never seek, restart, or replace the active media list.
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        fun ids()=items.map { it.mediaId }
        fun next(id: String)=enqueueMediaNext(player,MediaItem.Builder().setMediaId(id).build())
    }
    @Test fun movingEarlierOrLaterItemsToNextKeepsCurrentPlaybackAndAvoidsDuplicates() {
        val before=Queue(); before.next("a")
        assertEquals(listOf("b","a","c","d"),before.ids())
        val after=Queue(); after.next("d")
        assertEquals(listOf("a","b","d","c"),after.ids())
        after.next("d"); assertEquals(4,after.items.size)
    }
    @Test fun newItemsAreInsertedImmediatelyAfterCurrentWithoutRestartingIt() {
        val q=Queue(); q.next("new")
        assertEquals(listOf("a","b","new","c","d"),q.ids())
        q.next("b"); assertEquals(5,q.items.size)
    }
    @Test fun dragMoveClampsToBoundsAndCanMoveCurrentWithoutRestartingIt() {
        val q=Queue()
        assertTrue(movePlaybackItem(q.player,"b",1))
        assertEquals(listOf("a","c","b","d"),q.ids())
        assertTrue(movePlaybackItem(q.player,"b",99))
        assertFalse(movePlaybackItem(q.player,"b",1))
        assertFalse(movePlaybackItem(q.player,"missing",1))
        assertTrue(movePlaybackItem(q.player,"b",-99))
        assertEquals("b",q.ids().first())
    }
    @Test fun editingTurnsOffShuffleAndRejectsUnavailableCommandsWithoutMutation() {
        val q=Queue(); q.shuffle=true; q.next("d"); assertFalse(q.shuffle)
        q.shuffle=true; movePlaybackItem(q.player,"d",1); assertFalse(q.shuffle)
        val blocked=Queue(); blocked.denied+=Player.COMMAND_CHANGE_MEDIA_ITEMS
        assertFalse(movePlaybackItem(blocked.player,"d",-1))
        assertThrows(IllegalStateException::class.java) { blocked.next("a") }
        assertEquals(listOf("a","b","c","d"),blocked.ids())
        blocked.denied.clear(); blocked.shuffle=true; blocked.denied+=Player.COMMAND_SET_SHUFFLE_MODE
        assertFalse(movePlaybackItem(blocked.player,"d",-1))
        assertThrows(IllegalStateException::class.java) { blocked.next("a") }
        assertEquals(listOf("a","b","c","d"),blocked.ids())
    }
}
