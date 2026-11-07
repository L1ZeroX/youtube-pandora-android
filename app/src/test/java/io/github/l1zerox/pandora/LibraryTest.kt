package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    private val video=LibraryEntry("content://media/external/downloads/1","Night sky","video/mp4",120000,10)
    private val music=LibraryEntry("content://media/external/downloads/2","Morning song","audio/mpeg",60000,20)

    @Test fun importsNeverDuplicateDownloadsOrReplaceTheirMetadata() {
        val result=libraryEntries(listOf(video,music),listOf(video.copy(title="Import",imported=true)))
        assertEquals(listOf(music,video),result)
        assertFalse(result.last().imported)
    }
    @Test fun videoAndMusicFiltersCombineWithCaseInsensitiveSearch() {
        val entries=listOf(video,music)
        assertEquals(listOf(video),libraryEntries(entries,emptyList(),"動画"," NIGHT "))
        assertEquals(listOf(music),libraryEntries(entries,emptyList(),"音楽"))
        assertTrue(libraryEntries(entries,emptyList(),"動画","song").isEmpty())
    }
    @Test fun equalDatesHaveStableOrderingRegardlessOfProviderOrder() {
        val first=video.copy(added=20)
        assertEquals(libraryEntries(listOf(first,music),emptyList()),libraryEntries(listOf(music,first),emptyList()))
    }
    @Test fun onlyMediaMimeTypesOrKnownDownloadExtensionsAreAccepted() {
        assertEquals("audio/flac",libraryMime("audio/flac","track"))
        assertEquals("video/mp4",libraryMime("application/octet-stream","movie.MP4"))
        assertEquals("audio/mpeg",libraryMime(null,"music.MP3"))
        assertNull(libraryMime("text/plain","notes.txt"))
        assertNull(libraryMime(null,"movie.mp4.exe"))
    }
    @Test fun pipPreservesNormalLandscapePortraitAndPixelAspectRatios() {
        assertEquals(16f/9f,pipAspect(1920,1080),.001f)
        assertEquals(9f/16f,pipAspect(1080,1920),.001f)
        assertEquals(2f,pipAspect(100,100,2f),.001f)
    }
    @Test fun pipConstrainsUnsupportedShapesAndInvalidMetadata() {
        assertEquals(2.39f,pipAspect(Int.MAX_VALUE,1),.001f)
        assertEquals(1f/2.39f,pipAspect(1,Int.MAX_VALUE),.001f)
        for(ratio in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY)) assertEquals(16f/9f,pipAspect(1920,1080,ratio),.001f)
        assertEquals(16f/9f,pipAspect(0,0),.001f)
    }
}
