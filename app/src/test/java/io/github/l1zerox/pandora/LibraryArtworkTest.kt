package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryArtworkTest {
    private val song=LibraryEntry("content://song/1","Song","audio/mpeg",album="Ａｌｂｕｍ",artist="Singer")
    @Test fun songOverridesPrecedeTheSharedAutomaticAlbumAndNamesUseExistingNormalization() {
        val keys=libraryArtworkKeys(song,LibraryCollections())
        assertEquals(trackArtworkKey(song.uri),keys[0]); assertEquals(2,keys.size)
        assertEquals(keys[1],libraryArtworkKeys(song.copy(album=" album ",artist="Other"),LibraryCollections())[1])
    }
    @Test fun manualAlbumUsesItsOwnCoverAndMovingTracksChangesTheFallback() {
        val data=LibraryCollections(albums=listOf(UserPlaylist("album:manual","Manual",listOf(song.uri))))
        assertEquals(listOf(trackArtworkKey(song.uri),albumArtworkKey("album:manual")),libraryArtworkKeys(song,data))
        assertEquals(listOf(trackArtworkKey(song.uri),albumArtworkKey("album-unknown")),libraryArtworkKeys(song.copy(album=""),LibraryCollections()))
        assertEquals(emptyList<String>(),libraryArtworkKeys(song.copy(mime="video/mp4"),data))
    }
}
