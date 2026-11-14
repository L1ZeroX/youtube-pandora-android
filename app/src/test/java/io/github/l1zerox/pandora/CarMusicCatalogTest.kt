package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class CarMusicCatalogTest {
    private val a=LibraryEntry("content://music/a","First","audio/mpeg",durationMs=180_000,artist="Singer A",album="Ａｌｂｕｍ",track=1)
    private val b=a.copy(uri="content://music/b",title="Second",artist="Singer B",album="album",track=2)
    private val video=a.copy(uri="content://video/c",title="Video",mime="video/mp4")

    @Test fun carRootHasFourMusicCategoriesAndNeverExposesVideoOrRemoteInputUrls() {
        val catalog=CarMusicCatalog(listOf(a,b,video,a.copy(uri="https://example.com/a.mp3"),a.copy(uri="file:///private/a.mp3")),LibraryCollections())
        assertEquals(listOf(CAR_SONGS,CAR_ALBUMS,CAR_FAVORITES,CAR_PLAYLISTS),catalog.children(CAR_ROOT,0,Int.MAX_VALUE)!!.map { it.id })
        assertEquals(listOf(a,b),catalog.songs)
        assertNull(catalog.item(video.uri)); assertNull(catalog.queue("https://example.com/a.mp3"))
    }
    @Test fun normalizedAlbumNamesStayTogetherAndSelectingATrackKeepsTheAlbumOrderAndIndex() {
        val catalog=CarMusicCatalog(listOf(b,a),LibraryCollections())
        val albums=catalog.children(CAR_ALBUMS,0,80)!!; assertEquals(1,albums.size)
        val tracks=catalog.children(albums.single().id,0,80)!!
        val queue=catalog.queue(tracks[1].id)!!
        assertEquals(listOf(a,b),queue.entries); assertEquals(1,queue.index)
        assertEquals(a,catalog.coverEntry(albums.single()))
    }
    @Test fun favoritesAndPlaylistsResolveToTheirOwnQueuesAndFilterOutVideoAndDeletedFiles() {
        val data=LibraryCollections(favorites=setOf(b.uri,video.uri),playlists=listOf(UserPlaylist("one","List",listOf(b.uri,video.uri,a.uri,"content://missing",b.uri))))
        val catalog=CarMusicCatalog(listOf(a,b,video),data)
        val favorite=catalog.children(CAR_FAVORITES,0,80)!!.single()
        assertEquals(listOf(b),catalog.queue(favorite.id)!!.entries)
        val list=catalog.children(CAR_PLAYLISTS,0,80)!!.single()
        assertEquals(listOf(b,a),catalog.queue(list.id)!!.entries)
        val track=catalog.children(list.id,0,80)!![1]
        assertEquals(1,catalog.queue(track.id)!!.index)
    }
    @Test fun largeLibrariesUseBoundedBrowsePagesWithoutDroppingAnySongs() {
        val entries=(0..10_000).map { a.copy(uri="content://music/$it",title="Song $it",album="") }
        val catalog=CarMusicCatalog(entries,LibraryCollections())
        val pending=ArrayDeque<String>(); pending.add(CAR_SONGS)
        val found=mutableSetOf<String>()
        while(pending.isNotEmpty()) {
            val rows=catalog.children(pending.removeFirst(),0,Int.MAX_VALUE)!!
            assertTrue(rows.size <= CAR_PAGE_SIZE)
            rows.forEach { if(it.entry == null) pending.add(it.id) else assertTrue(found.add(it.entry.uri)) }
        }
        assertEquals(entries.map { it.uri }.toSet(),found)
    }
    @Test fun pagingDoesNotOverflowAndInvalidPagesAreRejected() {
        assertEquals(listOf(3,4),carPage((1..5).toList(),1,2))
        assertEquals(listOf(5),carPage((1..5).toList(),2,2))
        assertTrue(carPage(listOf(1),Int.MAX_VALUE,Int.MAX_VALUE).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { carPage(listOf(1),-1,10) }
        assertThrows(IllegalArgumentException::class.java) { carPage(listOf(1),0,0) }
    }
    @Test fun searchMatchesNormalizedTitlesArtistsAndAlbumsButNotVideosAndBoundsTheResults() {
        val catalog=CarMusicCatalog(listOf(a.copy(title="Ｓｏｎｇ"),b,video)+(1..100).map { a.copy(uri="content://extra/$it") },LibraryCollections())
        assertEquals("Ｓｏｎｇ",catalog.search(" song ").single().title)
        assertEquals(b,catalog.search("singer b").single().entry)
        assertEquals(CAR_PAGE_SIZE,catalog.search("ALBUM").size)
        assertTrue(catalog.search("Video").isEmpty()); assertTrue(catalog.search("  ").isEmpty())
    }
    @Test fun removedFavoritesOrTracksCannotBePlayedUsingAnOldBrowserId() {
        val data=LibraryCollections(favorites=setOf(a.uri))
        val old=CarMusicCatalog(listOf(a,b),data).children(CAR_FAVORITES,0,80)!!.single().id
        assertNull(CarMusicCatalog(listOf(a,b),LibraryCollections()).queue(old))
        assertNull(CarMusicCatalog(listOf(b),data).queue(a.uri))
    }
    @Test fun manualAlbumsAndIdsMatchThePhoneLibraryAndRemainStableAcrossReloads() {
        val data=LibraryCollections(albums=listOf(UserPlaylist("album:manual","Manual",listOf(a.uri,video.uri))))
        val catalog=CarMusicCatalog(listOf(a,b,video),data)
        val manual=catalog.children(CAR_ALBUMS,0,80)!!.first { it.title == "Manual" }
        assertEquals("album:manual",manual.album); assertEquals(listOf(a),catalog.queue(manual.id)!!.entries)
        val reloaded=CarMusicCatalog(listOf(video,b,a),data)
        assertEquals(catalog.children(CAR_SONGS,0,80),reloaded.children(CAR_SONGS,0,80))
    }
    @Test fun emptyOrUnknownFoldersNeverFallBackToAnUnrelatedSong() {
        val catalog=CarMusicCatalog(emptyList(),LibraryCollections())
        assertEquals(4,catalog.children(CAR_ROOT,0,80)!!.size)
        assertTrue(catalog.children(CAR_SONGS,0,80)!!.isEmpty())
        assertNull(catalog.queue(CAR_SONGS)); assertNull(catalog.children("unknown",0,80))
        assertNull(CarMusicCatalog(listOf(a),LibraryCollections()).queue(CAR_ALBUMS))
    }
    @Test fun requestedSeekPositionsRemainValidEvenWhenDurationIsNotYetKnown() {
        assertEquals(0L,carStartPosition(Long.MIN_VALUE,100_000))
        assertEquals(100_000L,carStartPosition(Long.MAX_VALUE,100_000))
        assertEquals(5000L,carStartPosition(5000,0))
        assertEquals(5000L,carStartPosition(5000,100_000))
    }
}
