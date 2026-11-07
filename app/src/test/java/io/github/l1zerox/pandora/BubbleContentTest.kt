package io.github.l1zerox.pandora

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class BubbleContentTest {
    @Test fun clipboardAcceptsYoutubeVideoFormsAndDeduplicatesThem() {
        val urls=clipboardYoutubeUrls("動画（https://youtu.be/AbCd_12-345?si=abc）。\nhttps://www.youtube.com/watch?v=AbCd_12-345&t=42\nhttps://m.youtube.com/shorts/ZyXw_98-765\nhttps://www.youtube.com/live/LIVE_123456")
        assertEquals(3,urls.size)
        assertEquals(listOf("AbCd_12-345","ZyXw_98-765","LIVE_123456"),urls.map(::youtubeVideoId))
    }
    @Test fun clipboardRejectsNonVideosSpoofedHostsCredentialsAndInvalidIds() {
        assertTrue(clipboardYoutubeUrls("https://youtube.com/playlist?list=abc https://youtube.com/@name https://evil-youtube.com/watch?v=AbCd_12-345 https://youtube.com.evil.test/watch?v=AbCd_12-345 https://user:secret@youtube.com/watch?v=AbCd_12-345 https://youtu.be/short https://youtu.be/AbCd_12-345%2Fmore https://youtu.be:8080/AbCd_12-345 https://example.com/video.mp4").isEmpty())
        assertNull(youtubeVideoId("file:///AbCd_12-345"))
    }
    @Test fun queuedYoutubeVideoHasThumbnailBeforeDownloadStarts() {
        val item=appendSharedVideos(emptyList(),"https://youtu.be/AbCd_12-345").single()
        assertFalse(item.resolved)
        assertEquals("https://i.ytimg.com/vi/AbCd_12-345/hqdefault.jpg",item.thumbnail)
        assertEquals("",youtubeThumbnail("https://example.com/a"))
        assertEquals(1,appendSharedVideos(listOf(item),"https://youtube.com/watch?v=AbCd_12-345").size)
    }
    @Test fun artworkFollowsCurrentDownloadThenNewestSelectedQueueItem() {
        val a=Video("a","a","A")
        val b=Video("b","b","B")
        val ignored=Video("c","c","C",selected=false)
        val state=ScreenState(videos=listOf(a,b,ignored))
        assertEquals(b,bubbleVideo(state))
        assertEquals(a,bubbleVideo(state.copy(busy=true,current="a")))
        assertEquals(a,bubbleVideo(state.copy(busy=true,paused=true,current="a")))
        assertEquals(a,bubbleVideo(state.copy(videos=listOf(a,b.copy(status="完了")))))
        assertNull(bubbleVideo(ScreenState()))
    }
    @Test fun thumbnailPaletteKeepsControlsReadableAndRingDistinct() {
        for(r in 0..255 step 51) for(g in 0..255 step 51) for(b in 0..255 step 51) {
            val p=bubblePalette(Color(r,g,b))
            assertTrue(1.05f/(p.accent.luminance()+.05f) >= 4.5f)
            assertTrue(1.05f/(p.surface.luminance()+.05f) >= 4.5f)
            assertTrue((p.ring.luminance()+.05f)/(p.track.luminance()+.05f) >= 3f)
        }
        assertNotEquals(bubblePalette(Color.Red),bubblePalette(Color.Blue))
    }
}
