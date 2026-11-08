package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class SavedDownloadIndexTest {
    private val video=Video("youtube:abcdefghijk","https://www.youtube.com/watch?v=abcdefghijk","Test")
    @Test fun sharedShortAndWatchUrlsIdentifyTheSameVideo() {
        val saved=listOf(SavedDownload(savedSource(video),"MP4","content://media/1"))
        assertTrue(saved.containsDownload(video.copy(key="shared:1",url="https://youtu.be/abcdefghijk?t=12"),"MP4"))
        assertTrue(saved.containsDownload(video.copy(url="https://www.youtube.com/shorts/abcdefghijk"),"MP4"))
        assertFalse(saved.containsDownload(video,"MP3"))
        assertFalse(saved.containsDownload(video.copy(url="https://youtu.be/lmnopqrstuv"),"MP4"))
    }
    @Test fun mp3AndMp4DownloadsAreIndependent() {
        val saved=listOf(SavedDownload(savedSource(video),"MP3","content://media/2"))
        assertTrue(saved.containsDownload(video,"MP3"))
        assertFalse(saved.containsDownload(video,"MP4"))
    }
    @Test fun legacyYouTubeFileNamesAndDuplicateSuffixAreRecognized() {
        assertEquals("youtube:abcdefghijk",legacySavedSource("Title [abcdefghijk].mp4"))
        assertEquals("youtube:abcdefghijk",legacySavedSource("Title [youtube_abcdefghijk] (2).mp3"))
        assertNull(legacySavedSource("Title [sm123456789].mp4"))
        assertNull(legacySavedSource("Title [abcdefghijk].mp4.part"))
        assertNull(legacySavedSource("abcdefghijk.mp3"))
    }
    @Test fun otherSitesUseTheirUrlSoSameIdAcrossSitesDoesNotCollide() {
        val a=Video("vimeo:123","https://vimeo.com/123","Vimeo")
        val b=Video("niconico:123","https://www.nicovideo.jp/watch/123","Nico")
        assertNotEquals(savedSource(a),savedSource(b))
        assertFalse(listOf(SavedDownload(savedSource(a),"MP4","a")).containsDownload(b,"MP4"))
        assertEquals(savedSource(a),savedSource(a.copy(key="shared:https://vimeo.com/123",url="https://vimeo.com/123#preview")))
    }
}
