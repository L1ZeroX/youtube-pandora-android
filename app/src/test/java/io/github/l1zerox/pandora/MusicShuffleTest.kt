package io.github.l1zerox.pandora

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class MusicShuffleTest {
    private val a=LibraryEntry("a","A","audio/mpeg",album="Album",track=2)
    private val b=a.copy(uri="b",title="B",track=1)
    private val c=a.copy(uri="c",title="C",album="Other")
    private val video=a.copy(uri="v",mime="video/mp4")
    @Test fun albumScopeUsesAlbumTrackOrderAndExcludesOtherAlbumsAndVideo() {
        assertEquals(listOf("b","a"),musicShuffleEntries(listOf(a,c,video,b),LibraryCollections(),"a",MusicShuffleScope.ALBUM).map { it.uri })
    }
    @Test fun manualAlbumMembershipOverridesEmbeddedAlbumNames() {
        val data=LibraryCollections(albums=listOf(UserPlaylist("album:manual","Mixed",listOf("a","c","v"))))
        assertEquals(setOf("a","c"),musicShuffleEntries(listOf(a,b,c,video),data,"a",MusicShuffleScope.ALBUM).map { it.uri }.toSet())
    }
    @Test fun allScopeIncludesOtherAlbumsAndUnclassifiedAudioExactlyOnce() {
        val unknown=a.copy(uri="u",album="")
        assertEquals(setOf("a","b","c","u"),musicShuffleEntries(listOf(a,b,c,unknown,video,a),LibraryCollections(),"b",MusicShuffleScope.ALL).map { it.uri }.toSet())
        assertEquals(4,musicShuffleEntries(listOf(a,b,c,unknown,video,a),LibraryCollections(),"b",MusicShuffleScope.ALL).size)
    }
    @Test fun unknownAlbumKeepsTheExistingUnclassifiedAlbumGrouping() {
        val unknown=a.copy(album=""); val other=c.copy(album="")
        assertEquals(setOf("a","c"),musicShuffleEntries(listOf(unknown,b,other),LibraryCollections(),"a",MusicShuffleScope.ALBUM).map { it.uri }.toSet())
    }
    @Test fun deletedAndVideoSelectionsAreRejected() {
        for(uri in listOf("missing","v")) try {
            musicShuffleEntries(listOf(a,video),LibraryCollections(),uri,MusicShuffleScope.ALL); fail("Invalid source accepted")
        } catch(_: IllegalArgumentException) { }
    }
    private class Queue {
        val items=mutableListOf("old","a","b").map { MediaItem.Builder().setMediaId(it).build() }.toMutableList()
        var index=1
        val position=12_345L
        val ready=false
        var shuffled=false
        val denied=mutableSetOf<Int>()
        val calls=mutableListOf<String>()
        val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
            when(method.name) {
                "getCurrentMediaItem" -> items[index]
                "getCurrentMediaItemIndex" -> index
                "getMediaItemCount" -> items.size
                "isCommandAvailable" -> args!![0] !in denied
                "removeMediaItems" -> {
                    val from=args!![0] as Int; val to=args[1] as Int
                    check(index !in from until to) { "Current item was removed" }
                    items.subList(from,to).clear(); if(to <= index) index-=to-from
                    calls+="remove"; null
                }
                "replaceMediaItem" -> { items[args!![0] as Int]=args[1] as MediaItem; calls+="metadata"; null }
                "addMediaItems" -> {
                    @Suppress("UNCHECKED_CAST") val added=args!!.last() as List<MediaItem>
                    val at=if(args.size == 1) items.size else args[0] as Int
                    items.addAll(at,added); if(at <= index) index+=added.size
                    calls+="add"; null
                }
                "setShuffleModeEnabled" -> { shuffled=args!![0] as Boolean; calls+="shuffle"; null }
                else -> error("Playback must not be restarted or sought: ${method.name}")
            }
        } as Player
        fun apply(vararg ids: String, scope: MusicShuffleScope = MusicShuffleScope.ALL, expected: String = "a") =
            applyMusicShuffle(player,expected,ids.map { MediaItem.Builder().setMediaId(it).build() },scope)
    }
    @Test fun expandingAndShrinkingRetainCurrentItemPositionAndPausedState() {
        val q=Queue()
        q.apply("b","a","c")
        assertEquals(listOf("b","a","c"),q.items.map { it.mediaId }); assertEquals(1,q.index); assertTrue(q.shuffled)
        q.apply("a",scope=MusicShuffleScope.ALBUM)
        assertEquals(listOf("a"),q.items.map { it.mediaId }); assertEquals(0,q.index)
        assertEquals(12_345L,q.position); assertFalse(q.ready)
    }
    @Test fun turningOffDoesNotReplaceTheCurrentQueue() {
        val q=Queue(); q.shuffled=true
        q.apply(scope=MusicShuffleScope.OFF)
        assertFalse(q.shuffled); assertEquals(listOf("old","a","b"),q.items.map { it.mediaId })
        assertEquals(listOf("shuffle"),q.calls)
    }
    @Test fun lateRequestAfterTrackChangeCannotModifyQueueOrShuffle() {
        val q=Queue(); q.index=2
        try { q.apply("a","b","c"); fail("Stale request applied") } catch(_: IllegalStateException) { }
        assertTrue(q.calls.isEmpty())
    }
    @Test fun unavailableCommandsAreRejectedBeforeAnyMutation() {
        for(command in listOf(Player.COMMAND_CHANGE_MEDIA_ITEMS,Player.COMMAND_SET_SHUFFLE_MODE)) {
            val q=Queue(); q.denied+=command
            try { q.apply("a","c"); fail("Denied command sent") } catch(_: IllegalStateException) { }
            assertTrue(q.calls.isEmpty())
        }
    }
    @Test fun missingCurrentItemAndDuplicatePlansAreRejectedWithoutMutation() {
        for(ids in listOf(arrayOf("c"),arrayOf("a","a"))) {
            val q=Queue()
            try { q.apply(*ids); fail("Invalid queue plan applied") } catch(_: IllegalArgumentException) { }
            assertTrue(q.calls.isEmpty())
        }
    }
}
