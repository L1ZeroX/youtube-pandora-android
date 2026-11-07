package io.github.l1zerox.pandora

import org.junit.Assert.*
import org.junit.Test

class BubbleQueueActionsTest {
    private val a=Video("a","https://example.com/a","A",status="完了",savedUri="content://media/1")
    private val b=Video("b","https://youtu.be/AbCd_12-345","B",selected=false)
    @Test fun changingFormatPreservesQueueAndOtherPreferences() {
        val state=ScreenState(videos=listOf(a,b),preferences=Preferences(quality="最高",audio="320kbps",bubble=true))
        val mp3=state.withDownloadFormat("MP3")
        assertEquals("MP3",mp3.preferences.format)
        assertEquals(state.preferences.copy(format="MP3"),mp3.preferences)
        assertSame(state.videos,mp3.videos)
        assertEquals(state,mp3.withDownloadFormat("MP4"))
        assertSame(state,state.withDownloadFormat("EXE"))
    }
    @Test fun downloadingPausedAndStoppingStatesRejectFormatChangesAndDeletion() {
        val state=ScreenState(videos=listOf(a,b),busy=true,current="b",operation="DOWNLOAD")
        for(active in listOf(state,state.copy(paused=true),state.copy(stopping=true))) {
            assertSame(active,active.withDownloadFormat("MP3"))
            assertSame(active,active.withoutQueueVideo("a"))
            assertSame(active,active.withoutQueueVideo("b"))
        }
    }
    @Test fun deletingQueueEntryPreservesOtherEntriesAndSavedFileReferences() {
        val state=ScreenState(videos=listOf(a,b))
        val result=state.withoutQueueVideo("b")
        assertEquals(listOf(a),result.videos)
        assertSame(a,result.videos.single())
        assertEquals("content://media/1",result.videos.single().savedUri)
        assertEquals(state.preferences,result.preferences)
        assertSame(state,state.withoutQueueVideo("missing"))
        assertTrue(result.withoutQueueVideo("a").videos.isEmpty())
        assertEquals("content://media/1",a.savedUri)
    }
    @Test fun sharedYoutubeLinkUpdatesBubbleSelectionWithoutOpeningPanel() {
        val state=ScreenState(videos=listOf(a))
        val shared=state.copy(videos=appendSharedVideos(state.videos,"https://youtu.be/AbCd_12-345"))
        val video=bubbleVideo(shared)!!
        assertEquals("AbCd_12-345",youtubeVideoId(video.url))
        assertTrue(video.thumbnail.endsWith("/AbCd_12-345/hqdefault.jpg"))
        assertEquals(a,bubbleVideo(shared.copy(busy=true,current=a.key)))
        assertEquals(a,bubbleVideo(shared.withoutQueueVideo(video.key)))
    }
}
