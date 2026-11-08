package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryCollectionsTest {
    private val video=LibraryEntry("v","Beta","video/mp4",120000,20,autoDubbed=true)
    private val short=LibraryEntry("s","Alpha","video/mp4",40000,30,width=720,height=1280)
    private val music=LibraryEntry("m","Music","audio/mpeg",180000,10)
    private val unknown=LibraryEntry("u","Unknown","video/mp4",0,40)
    private val entries=listOf(video,short,music,unknown)
    @Test fun collectionsSurviveSerializationAndKeepPlaylistOrder() {
        val data=LibraryCollections(setOf("v","m"),listOf(UserPlaylist("p","通勤用",listOf("m","v"))))
        assertEquals(data,LibraryCollections.parse(data.json()))
        assertEquals(listOf(music,video),organizedLibrary(entries,data,"p",LibrarySort.NEWEST,false))
    }
    @Test fun addingIsIdempotentAndDeletingAnItemCleansAllListsOnlyForThatUri() {
        val data=LibraryCollections(setOf("v","m"),listOf(UserPlaylist("p","A",listOf("m")),UserPlaylist("q","B",listOf("v"))))
            .addTo("p",listOf("v","m","v"))
        assertEquals(listOf("m","v"),data.playlists[0].uris)
        val removed=data.without("v")
        assertEquals(setOf("m"),removed.favorites)
        assertEquals(listOf("m"),removed.playlists[0].uris)
        assertTrue(removed.playlists[1].uris.isEmpty())
    }
    @Test fun malformedSavedDataDoesNotPreventOpeningTheLibrary() {
        assertEquals(LibraryCollections(),LibraryCollections.parse("broken"))
        val data=LibraryCollections.parse("""{"favorites":["a","a",""],"playlists":[{"id":"p","name":" Test ","uris":["a","a"]},{"id":"","name":"bad"}]}""")
        assertEquals(setOf("a"),data.favorites)
        assertEquals(listOf(UserPlaylist("p","Test",listOf("a"))),data.playlists)
    }
    @Test fun sortingIsDeterministicAndUnknownDurationIsLastInShortestOrder() {
        fun sorted(sort: LibrarySort)=organizedLibrary(entries,LibraryCollections(),"",sort,false).map { it.uri }
        assertEquals(listOf("u","s","v","m"),sorted(LibrarySort.NEWEST))
        assertEquals(listOf("m","v","s","u"),sorted(LibrarySort.OLDEST))
        assertEquals(listOf("s","v","m","u"),sorted(LibrarySort.TITLE))
        assertEquals(listOf("m","v","s","u"),sorted(LibrarySort.LONGEST))
        assertEquals(listOf("s","v","m","u"),sorted(LibrarySort.SHORTEST))
    }
    @Test fun favoritesAndDubbedFilterIntersectWithoutIncludingDubbedMusic() {
        val data=LibraryCollections(setOf("v","m"))
        assertEquals(listOf(video),organizedLibrary(entries+music.copy(uri="other",autoDubbed=true),data,"favorites",LibrarySort.NEWEST,true))
        assertEquals(listOf(video),organizedLibrary(entries+music.copy(uri="other",autoDubbed=true),data,"",LibrarySort.NEWEST,true))
        assertEquals(listOf(video,music),organizedLibrary(entries,data,"favorites",LibrarySort.NEWEST,false))
    }
    @Test fun resumeIgnoresTheBeginningFinishedAndUnknownDuration() {
        assertEquals(16000L,resumablePosition(16000,263000))
        assertEquals(0L,resumablePosition(4999,263000))
        assertEquals(0L,resumablePosition(260000,263000))
        assertEquals(0L,resumablePosition(16000,0))
        assertEquals(0L,resumablePosition(9000,10000))
        assertEquals(0L,resumablePosition(-1,263000))
    }
}
