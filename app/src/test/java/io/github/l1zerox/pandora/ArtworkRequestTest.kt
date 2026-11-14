package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class ArtworkRequestTest {
    @Test fun switchingAtoBtoARejectsTheFirstImageEvenWhenTheUriMatchesAgain() {
        val requests=ArtworkRequest()
        val a=LibraryEntry("content://song/a","A","audio/mpeg",album="Album A")
        val first=requests.begin(a)!!
        val second=requests.begin(a.copy(uri="content://song/b",album="Album B"))!!
        val latest=requests.begin(a)!!
        assertFalse(requests.accepts(first)); assertFalse(requests.accepts(second)); assertTrue(requests.accepts(latest))
        assertNull(requests.begin(a))
    }
    @Test fun replacingACoverOrResolvingAlbumMetadataInvalidatesAnInFlightImage() {
        val requests=ArtworkRequest(); val song=LibraryEntry("content://song/a","A","audio/mpeg")
        val first=requests.begin(song)!!
        val tagged=requests.begin(song.copy(album="Album"))!!
        assertFalse(requests.accepts(first)); assertTrue(requests.accepts(tagged))
        requests.invalidate()
        assertFalse(requests.accepts(tagged))
        val replacement=requests.begin(song.copy(album="Album"))!!
        assertTrue(requests.accepts(replacement))
    }
}
